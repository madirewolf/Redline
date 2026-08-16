package com.redline.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.redline.app.data.local.entity.PrTrack
import com.redline.app.ui.theme.OnSurfaceVariant
import com.redline.app.ui.theme.Red500
import com.redline.app.ui.theme.SurfaceVariant

@Composable
fun MusicScreen(viewModel: MusicViewModel = hiltViewModel()) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    var label by remember { mutableStateOf("") }
    var uri by remember { mutableStateOf("") }
    var dropSeconds by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Music",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "PR tracks for heavy attempts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                }
                Button(
                    onClick = viewModel::playDefault,
                    enabled = tracks.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Red500),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Play PR")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceVariant, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Add track",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Label") },
                    placeholder = { Text("Bench PR") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uri,
                    onValueChange = { uri = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Spotify URI or URL") },
                    placeholder = { Text("spotify:track:...") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dropSeconds,
                    onValueChange = { dropSeconds = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Drop time seconds") },
                    placeholder = { Text("42") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.addTrack(label, uri, dropSeconds)
                        label = ""
                        uri = ""
                        dropSeconds = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Red500),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save PR Track")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (tracks.isEmpty()) {
                Text(
                    text = "Add a Spotify track URL or URI. During a workout, say \"play my PR\" to auto-queue it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Text(
                    text = "Your PR Tracks (${tracks.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        items(tracks, key = { it.id }) { track ->
            TrackCard(
                track = track,
                onPlay = { viewModel.play(track) },
                onDefault = { viewModel.setDefault(track) },
                onDelete = { viewModel.delete(track) }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun TrackCard(
    track: PrTrack,
    onPlay: () -> Unit,
    onDefault: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceVariant, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (track.isDefault) "Default PR track" else "Saved PR track",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (track.isDefault) Red500 else OnSurfaceVariant
                )
            }
            Text(
                text = "${track.dropTimestampMs / 1000}s",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.trackUri,
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row {
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(containerColor = Red500),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Play")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onDefault,
                enabled = !track.isDefault,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Default")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onDelete,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Delete")
            }
        }
    }
}
