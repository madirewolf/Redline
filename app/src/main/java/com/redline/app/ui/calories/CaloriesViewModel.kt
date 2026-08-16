package com.redline.app.ui.calories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redline.app.data.local.entity.CalorieEntry
import com.redline.app.data.repository.WorkoutRepository
import com.redline.app.nutrition.FoodResult
import com.redline.app.nutrition.NutritionApi
import com.redline.app.voice.SpeechRecognizerManager
import com.redline.app.voice.SpeechState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class CalorieUiState(
    val searchResults: List<FoodResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val lastQuery: String? = null
)

@HiltViewModel
class CaloriesViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val nutritionApi: NutritionApi,
    val speechManager: SpeechRecognizerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalorieUiState())
    val uiState: StateFlow<CalorieUiState> = _uiState.asStateFlow()

    val speechState: StateFlow<SpeechState> = speechManager.state

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
                if (state is SpeechState.Result) {
                    searchFood(state.text)
                }
            }
        }
    }

    fun startListening() = speechManager.startListening()

    fun searchFood(query: String) {
        viewModelScope.launch {
            _uiState.value = CalorieUiState(isSearching = true, lastQuery = query)
            val results = nutritionApi.searchFood(query)
            _uiState.value = if (results.isEmpty()) {
                CalorieUiState(searchError = "No results for \"$query\". Try a simpler term.", lastQuery = query)
            } else {
                CalorieUiState(searchResults = results, lastQuery = query)
            }
            speechManager.resetState()
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
        speechManager.resetState()
    }
}
