package com.redline.app.ui.community

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.redline.app.ui.components.EmptyState
import com.redline.app.ui.components.RedlineTopBar
import java.time.Instant
import java.time.ZoneId

@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val conversation = state.conversation
    val member = conversation?.member
    val zone = remember { ZoneId.systemDefault() }
    val today = Instant.ofEpochMilli(rememberNow()).atZone(zone).toLocalDate()
    val listState = rememberLazyListState()
    var draft by rememberSaveable { mutableStateOf("") }

    ResizeForKeyboard()

    // Newest at the bottom: the list is laid out in reverse, so item 0 is the latest message.
    val newestKey = state.rows.lastOrNull()?.key
    LaunchedEffect(newestKey) {
        if (newestKey != null) listState.animateScrollToItem(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
    ) {
        RedlineTopBar(
            title = member?.name.orEmpty(),
            onBack = onBack,
            actions = {
                if (member != null) {
                    IconButton(
                        onClick = { onOpenProfile(member.id) },
                        modifier = Modifier.semantics { contentDescription = "${member.name}'s profile" }
                    ) {
                        Avatar(member.name, member.id, size = 32.dp)
                    }
                }
            }
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> Unit
                conversation == null -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = "Conversation not found",
                    body = "Go back and pick someone from Messages.",
                    modifier = Modifier.padding(top = 48.dp)
                )
                state.rows.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.Forum,
                    title = "Say hi to ${firstName(conversation.member.name)}",
                    body = "Messages are saved on this phone. Nobody else gets them until Redline has accounts.",
                    modifier = Modifier.padding(top = 48.dp)
                )
                else -> LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    items(state.rows.asReversed(), key = { it.key }) { row ->
                        when (row) {
                            is DayRow -> DayHeader(dayLabel(row.date, today))
                            is BubbleRow -> Bubble(row, theirName = firstName(conversation.member.name), zone = zone)
                        }
                    }
                }
            }
        }

        if (conversation != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MessageInput(
                value = draft,
                onValueChange = { draft = it },
                placeholder = "Message ${firstName(conversation.member.name)}",
                onSend = {
                    viewModel.send(draft)
                    draft = ""
                }
            )
        }
    }
}

@Composable
private fun DayHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp)
            .semantics { heading() }
    )
}

/**
 * One message. Yours sit on the right in red, theirs on the left in grey.
 * Within a run from the same person the bubbles sit close, the corners on the
 * sender's side tighten, and only the last one shows the time.
 */
@Composable
private fun Bubble(row: BubbleRow, theirName: String, zone: ZoneId) {
    val colors = MaterialTheme.colorScheme
    val message = row.message
    val mine = message.fromMe
    val round = 18.dp
    val tight = 6.dp
    val shape = if (mine) {
        RoundedCornerShape(topStart = round, topEnd = if (row.startsRun) round else tight, bottomEnd = tight, bottomStart = round)
    } else {
        RoundedCornerShape(topStart = if (row.startsRun) round else tight, topEnd = round, bottomEnd = round, bottomStart = tight)
    }
    val time = messageTime(message.sentAt, zone)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (row.startsRun) 10.dp else 2.dp)
            .clearAndSetSemantics {
                contentDescription = "${if (mine) "You" else theirName}: ${message.text}, $time"
            },
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(start = if (mine) 48.dp else 0.dp, end = if (mine) 0.dp else 48.dp)
                .clip(shape)
                .background(if (mine) colors.primaryContainer else colors.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Text(
                message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (mine) colors.onPrimaryContainer else colors.onSurface
            )
        }
        if (row.endsRun) {
            Text(
                time,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
private fun MessageInput(value: String, onValueChange: (String) -> Unit, placeholder: String, onSend: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(placeholder) },
            maxLines = 5,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        Spacer(modifier = Modifier.width(8.dp))
        FilledIconButton(
            onClick = onSend,
            enabled = value.isNotBlank(),
            modifier = Modifier.padding(bottom = 4.dp).size(48.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}

/**
 * The app doesn't set a soft input mode, so the keyboard could pan the whole
 * window and push the top bar off screen. While a chat is open, resize instead
 * so the input sits on the keyboard; put the old mode back on the way out.
 */
@Composable
private fun ResizeForKeyboard() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val original = window?.attributes?.softInputMode
        if (window != null && original != null) {
            @Suppress("DEPRECATION")
            window.setSoftInputMode(
                (original and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        }
        onDispose {
            if (window != null && original != null) window.setSoftInputMode(original)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
