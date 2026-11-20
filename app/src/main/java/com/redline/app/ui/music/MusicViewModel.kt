package com.redline.app.ui.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redline.app.data.local.entity.PrTrack
import com.redline.app.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** Shown under the list when a track can't be opened. */
    private val _playError = MutableStateFlow<String?>(null)
    val playError: StateFlow<String?> = _playError.asStateFlow()

    /** Saves the track and returns true, or returns false if the link isn't one we can open. */
    fun addTrack(label: String, uri: String, dropSeconds: String): Boolean {
        val cleanUri = uri.trim()
        if (!isTrackLink(cleanUri)) return false
        val cleanLabel = label.trim().ifBlank { "PR track" }
        val dropMs = ((dropSeconds.trim().toFloatOrNull() ?: 0f) * 1000).toLong()

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
        return true
    }

    fun play(track: PrTrack) {
        _playError.value = if (musicController.play(track)) null
        else "Nothing on this phone can open “${track.label}”. Install Spotify, or use an open.spotify.com link."
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
            // Keep "play my PR" working: the next track takes over as default.
            if (track.isDefault) {
                tracks.value.firstOrNull { it.id != track.id }
                    ?.let { repository.updatePrTrack(it.copy(isDefault = true)) }
            }
        }
    }

    companion object {
        fun isTrackLink(uri: String): Boolean {
            val lower = uri.trim().lowercase()
            return lower.startsWith("spotify:") || lower.startsWith("https://") || lower.startsWith("http://")
        }
    }
}
