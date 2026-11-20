package com.redline.app.ui.calories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redline.app.data.local.entity.CalorieEntry
import com.redline.app.data.repository.WorkoutRepository
import com.redline.app.nutrition.FoodResult
import com.redline.app.nutrition.FoodSearch
import com.redline.app.nutrition.NutritionApi
import com.redline.app.settings.SettingsStore
import com.redline.app.voice.SpeechRecognizerManager
import com.redline.app.voice.SpeechState
import com.redline.app.voice.forOwner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class CalorieUiState(
    val searchResults: List<FoodResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val lastQuery: String? = null,
    /** True when the lookup can't run at all until a key is added in Settings. */
    val needsKey: Boolean = false
)

@HiltViewModel
class CaloriesViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val nutritionApi: NutritionApi,
    private val settingsStore: SettingsStore,
    val speechManager: SpeechRecognizerManager
) : ViewModel() {

    val hasKey: StateFlow<Boolean> = settingsStore.usdaApiKey
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.usdaApiKey.value.isNotBlank())

    private val _uiState = MutableStateFlow(CalorieUiState())
    val uiState: StateFlow<CalorieUiState> = _uiState.asStateFlow()

    val speechState: StateFlow<SpeechState> = speechManager.state
        .map { it.forOwner(OWNER) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SpeechState.Idle)

    private val todayStart: Long
        get() {
            val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
            return start.toInstant().toEpochMilli()
        }

    private val todayEnd: Long
        get() = todayStart + 86400000L

    val todayEntries = repository.getCalorieEntriesForDay(todayStart, todayEnd)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            speechManager.state.collect { state ->
                if (state is SpeechState.Result && state.owner == OWNER) {
                    speechManager.consume(OWNER)
                    searchFood(state.text)
                }
            }
        }
    }

    fun startListening() = speechManager.startListening(OWNER)

    fun stopListening() = speechManager.stopListening()

    fun searchFood(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = CalorieUiState(isSearching = true, lastQuery = clean)
            _uiState.value = when (val result = nutritionApi.searchFood(clean)) {
                is FoodSearch.Found -> CalorieUiState(searchResults = result.results, lastQuery = clean)
                FoodSearch.NoKey -> CalorieUiState(needsKey = true, lastQuery = clean)
                FoodSearch.NoMatch -> CalorieUiState(searchError = "Nothing found for “$clean”. Try a simpler name, like “banana”.", lastQuery = clean)
                is FoodSearch.Failed -> CalorieUiState(searchError = "${result.reason}.", lastQuery = clean)
            }
        }
    }

    fun logFood(result: FoodResult, servings: Float = 1f) {
        viewModelScope.launch {
            repository.logCalorie(
                CalorieEntry(
                    foodName = result.name,
                    calories = result.calories * servings,
                    protein = result.protein?.times(servings),
                    carbs = result.carbs?.times(servings),
                    fat = result.fat?.times(servings),
                    servingSize = result.servingSize * servings,
                    unit = result.unit
                )
            )
            _uiState.value = CalorieUiState()
        }
    }

    fun deleteEntry(entry: CalorieEntry) {
        viewModelScope.launch { repository.deleteCalorieEntry(entry) }
    }

    fun clearSearch() {
        _uiState.value = CalorieUiState()
        speechManager.consume(OWNER)
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.release(OWNER)
    }

    private companion object {
        const val OWNER = "calories"
    }
}
