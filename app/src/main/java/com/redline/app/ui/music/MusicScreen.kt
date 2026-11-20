package com.redline.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.redline.app.data.local.entity.PrTrack
import com.redline.app.ui.components.EmptyState
import com.redline.app.ui.components.RedlineTopBar
import com.redline.app.ui.components.ScreenPadding
import com.redline.app.ui.components.SectionCard
import com.redline.app.ui.components.SectionLabel

@Composable
fun MusicScreen(onBack: () -> Unit, viewModel: MusicViewModel = hiltViewModel()) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val playError by viewModel.playError.collectAsStateWithLifecycle()
    var label by rememberSaveable { mutableStateOf("") }
    var uri by rememberSaveable { mutableStateOf("") }
    var dropSeconds by rememberSaveable { mutableStateOf("") }
    var linkError by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize()) {
        RedlineTopBar(title = "PR music", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Save the song you want for a heavy attempt. Mid-workout, say “play my PR” and it opens in Spotify.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }

            item {
                SectionCard {
                    Text("Add a track", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uri,
                        onValueChange = { uri = it; linkError = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Spotify link") },
                        placeholder = { Text("https://open.spotify.com/track/…") },
                        isError = linkError,
                        supportingText = if (linkError) {
                            { Text("Paste a Spotify link or a spotify:track: URI") }
                        } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it },
                            modifier = Modifier.weight(1.6f),
                            label = { Text("Name") },
                            placeholder = { Text("Optional") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)
                        )
                        OutlinedTextField(
                            value = dropSeconds,
                            onValueChange = { dropSeconds = it.filter { c -> c.isDigit() }.take(4) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Drop at (s)") },
                            placeholder = { Text("Optional") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (viewModel.addTrack(label, uri, dropSeconds)) {
                                label = ""; uri = ""; dropSeconds = ""
                            } else {
                                linkError = true
                            }
                        },
                        enabled = uri.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Save track") }
                }
            }

            playError?.let { message ->
                item {
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.error)
                }
            }

            if (tracks.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.MusicNote,
                        title = "No PR tracks yet",
                        body = "The first one you save becomes the one “play my PR” opens."
                    )
                }
            } else {
                item { SectionLabel("Your tracks", Modifier.padding(top = 8.dp)) }
                items(tracks, key = { it.id }) { track ->
                    TrackCard(
                        track = track,
                        onPlay = { viewModel.play(track) },
                        onDefault = { viewModel.setDefault(track) },
                        onDelete = { viewModel.delete(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackCard(
    track: PrTrack,
    onPlay: () -> Unit,
    onDefault: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    SectionCard(contentPadding = 0.dp) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(top = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        track.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.isDefault) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Default",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onPrimaryContainer,
                            modifier = Modifier
                                .background(colors.primaryContainer, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    buildString {
                        if (track.dropTimestampMs > 0) append("Drop at ${track.dropTimestampMs / 1000}s  ·  ")
                        append(track.trackUri)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete ${track.label}", tint = colors.onSurfaceVariant)
            }
        }
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Play")
            }
            if (!track.isDefault) {
                TextButton(onClick = onDefault) { Text("Make default") }
            }
        }
    }
}
