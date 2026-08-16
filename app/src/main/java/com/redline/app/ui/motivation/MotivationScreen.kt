package com.redline.app.ui.motivation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.redline.app.data.local.entity.MotivationalVideo
import com.redline.app.data.repository.WorkoutRepository
import com.redline.app.ui.theme.OnSurfaceVariant
import com.redline.app.ui.theme.Red500
import com.redline.app.ui.theme.SurfaceVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MotivationViewModel @Inject constructor(
    private val repository: WorkoutRepository
) : ViewModel() {
    val videos = repository.getVideos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addVideo(title: String, url: String) {
        if (url.isBlank()) return
        val type = if (url.contains("youtube") || url.contains("youtu.be")) "youtube" else "mp4"
        viewModelScope.launch {
            repository.addVideo(
                MotivationalVideo(
                    title = title.ifBlank { "Motivation" },
                    url = url.trim(),
                    type = type
                )
            )
        }
    }

    fun deleteVideo(video: MotivationalVideo) {
        viewModelScope.launch { repository.deleteVideo(video) }
    }
}

@Composable
fun MotivationScreen(viewModel: MotivationViewModel = hiltViewModel()) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Motivation", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("Curated videos to keep you going.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        }

        // Add video form
        item {
            Column(
                modifier = Modifier.fillMaxWidth().background(SurfaceVariant, RoundedCornerShape(12.dp)).padding(16.dp)
            ) {
                Text("Add Video", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Title") }, placeholder = { Text("Never give up") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("URL") }, placeholder = { Text("https://youtube.com/... or MP4 link") }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.addVideo(title, url); title = ""; url = "" },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Red500),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add")
                }
            }
        }

        if (videos.isNotEmpty()) {
            item { Text("Your Videos (${videos.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface) }
        }

        items(videos, key = { it.id }) { video ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceVariant).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Red500)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(video.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        if (video.type == "youtube") "YouTube" else "Video",
                        style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.deleteVideo(video) }) {
                    Icon(Icons.Default.Delete, "Delete", tint = OnSurfaceVariant)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
