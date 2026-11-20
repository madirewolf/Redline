package com.redline.app.ui.motivation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.redline.app.data.local.entity.MotivationalVideo
import com.redline.app.data.repository.WorkoutRepository
import com.redline.app.ui.components.EmptyState
import com.redline.app.ui.components.RedlineTopBar
import com.redline.app.ui.components.ScreenPadding
import com.redline.app.ui.components.SectionCard
import com.redline.app.ui.components.SectionLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MotivationViewModel @Inject constructor(
    private val repository: WorkoutRepository
) : ViewModel() {
    val videos = repository.getVideos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _openError = MutableStateFlow<String?>(null)
    val openError: StateFlow<String?> = _openError.asStateFlow()

    /** Saves the video and returns true, or returns false if the link isn't a web address. */
    fun addVideo(title: String, url: String): Boolean {
        val clean = url.trim()
        if (!isVideoLink(clean)) return false
        viewModelScope.launch {
            repository.addVideo(
                MotivationalVideo(
                    title = title.trim().ifBlank { "Motivation" },
                    url = clean,
                    type = if (isYouTube(clean)) "youtube" else "mp4"
                )
            )
        }
        return true
    }

    /** Hands the link to YouTube, a video player or the browser, whichever claims it. */
    fun open(context: Context, video: MotivationalVideo) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(video.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        _openError.value = try {
            context.startActivity(intent)
            null
        } catch (e: ActivityNotFoundException) {
            "Nothing on this phone can open “${video.title}”."
        }
    }

    fun deleteVideo(video: MotivationalVideo) {
        viewModelScope.launch { repository.deleteVideo(video) }
    }

    companion object {
        fun isVideoLink(url: String): Boolean {
            val lower = url.trim().lowercase()
            return (lower.startsWith("https://") || lower.startsWith("http://")) && lower.length > 10
        }

        fun isYouTube(url: String): Boolean = url.contains("youtube.com") || url.contains("youtu.be")
    }
}

@Composable
fun MotivationScreen(onBack: () -> Unit, viewModel: MotivationViewModel = hiltViewModel()) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val openError by viewModel.openError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var title by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var urlError by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        RedlineTopBar(title = "Motivation", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Keep the videos that get you to the gym. Tap one to watch it in YouTube or your video player.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }

            item {
                SectionCard {
                    Text("Add a video", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it; urlError = false },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Link") },
                        placeholder = { Text("https://youtube.com/watch?v=…") },
                        isError = urlError,
                        supportingText = if (urlError) {
                            { Text("Paste a full link that starts with https://") }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Title") },
                        placeholder = { Text("Optional") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (viewModel.addVideo(title, url)) {
                                title = ""; url = ""
                            } else {
                                urlError = true
                            }
                        },
                        enabled = url.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Save video") }
                }
            }

            openError?.let { message ->
                item { Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.error) }
            }

            if (videos.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.SmartDisplay,
                        title = "No videos yet",
                        body = "Saved videos show up here, newest first."
                    )
                }
            } else {
                item { SectionLabel("Your videos", Modifier.padding(top = 8.dp)) }
                item {
                    SectionCard(contentPadding = 0.dp) {
                        videos.forEachIndexed { index, video ->
                            if (index > 0) HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                            VideoRow(
                                video = video,
                                onOpen = { viewModel.open(context, video) },
                                onDelete = { viewModel.deleteVideo(video) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoRow(video: MotivationalVideo, onOpen: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Watch", onClick = onOpen)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = colors.primaryContainer, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.padding(8.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                video.title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (video.type == "youtube") "YouTube" else Uri.parse(video.url).host ?: "Video",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete ${video.title}", tint = colors.onSurfaceVariant)
        }
    }
}
