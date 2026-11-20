package com.redline.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redline.app.data.sample.SampleDataCounts
import com.redline.app.data.sample.SampleDataSeeder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Settings > Sample data, in debug and demo builds only. */
@HiltViewModel
class SampleDataViewModel @Inject constructor(
    private val seeder: SampleDataSeeder
) : ViewModel() {

    val isLoaded: StateFlow<Boolean> = seeder.isLoaded
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** What the last button press did, shown under the buttons. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun load() = perform("Couldn't load sample data") {
        val added = seeder.load()
        if (added.isEmpty) "Sample data is already loaded." else "Added ${describe(added)}."
    }

    fun clear() = perform("Couldn't remove sample data") {
        val removed = seeder.clear()
        if (removed.isEmpty) "No sample data to remove." else "Removed ${describe(removed)}."
    }

    private fun perform(failure: String, action: suspend () -> String) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            _message.value = try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "$failure: ${e.message ?: e.javaClass.simpleName}."
            } finally {
                _busy.value = false
            }
        }
    }

    companion object {
        /** "16 workouts, 101 meals, 10 videos and 3 PR tracks", leaving out what's zero. */
        internal fun describe(counts: SampleDataCounts): String {
            val parts = listOfNotNull(
                plural(counts.workouts, "workout"),
                plural(counts.meals, "meal"),
                plural(counts.videos, "video"),
                plural(counts.tracks, "PR track")
            ).ifEmpty { listOfNotNull(plural(counts.sets, "set")) }
            return when (parts.size) {
                0 -> "nothing"
                1 -> parts.single()
                else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
            }
        }

        private fun plural(count: Int, word: String): String? = when (count) {
            0 -> null
            1 -> "1 $word"
            else -> "$count ${word}s"
        }
    }
}
