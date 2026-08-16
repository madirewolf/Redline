package com.redline.app.ui.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redline.app.data.local.entity.PrTrack
import com.redline.app.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val musicController: MusicController
) : ViewModel() {
    val tracks: StateFlow<List<PrTrack>> = repository.getPrTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTrack(label: String, uri: String, dropSeconds: String) {
        val cleanLabel = label.trim().ifBlank { "PR Track" }
        val cleanUri = uri.trim()
        if (cleanUri.isBlank()) return
        val dropMs = ((dropSeconds.toFloatOrNull() ?: 0f) * 1000).toLong()

        viewModelScope.launch {
            repository.addPrTrack(
                PrTrack(
                    label = cleanLabel,
                    trackUri = cleanUri,
                    dropTimestampMs = dropMs.coerceAtLeast(0),
                    isDefault = tracks.value.isEmpty()
                )
            )
        }
    }

    fun play(track: PrTrack) {
        musicController.play(track)
    }

    fun playDefault() {
        viewModelScope.launch {
            val track = repository.getDefaultPrTrack() ?: tracks.value.firstOrNull()
            if (track != null) musicController.play(track)
        }
    }

    fun setDefault(track: PrTrack) {
        viewModelScope.launch {
            tracks.value.forEach { current ->
                repository.updatePrTrack(current.copy(isDefault = current.id == track.id))
            }
        }
    }

    fun delete(track: PrTrack) {
        viewModelScope.launch {
            repository.deletePrTrack(track)
        }
    }
}
