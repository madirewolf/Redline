package com.redline.app.ui.settings

import androidx.lifecycle.ViewModel
import com.redline.app.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore
) : ViewModel() {
    val userName: StateFlow<String> = settingsStore.userName
    val llmEndpoint: StateFlow<String> = settingsStore.llmEndpoint
    val defaultUnit: StateFlow<String> = settingsStore.defaultUnit

    fun setUserName(value: String) {
        settingsStore.setUserName(value)
    }

    fun setLlmEndpoint(value: String) {
        settingsStore.setLlmEndpoint(value)
    }

    fun setDefaultUnit(value: String) {
        settingsStore.setDefaultUnit(value)
    }
}
