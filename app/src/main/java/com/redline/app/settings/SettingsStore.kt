package com.redline.app.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("redline_settings", Context.MODE_PRIVATE)

    private val _llmEndpoint = MutableStateFlow(prefs.getString(KEY_LLM_ENDPOINT, "").orEmpty())
    val llmEndpoint: StateFlow<String> = _llmEndpoint.asStateFlow()

    private val _defaultUnit = MutableStateFlow(prefs.getString(KEY_DEFAULT_UNIT, "lbs").orEmpty())
    val defaultUnit: StateFlow<String> = _defaultUnit.asStateFlow()

    fun setLlmEndpoint(value: String) {
        val cleanValue = value.trim()
        prefs.edit().putString(KEY_LLM_ENDPOINT, cleanValue).apply()
        _llmEndpoint.value = cleanValue
    }

    fun setDefaultUnit(value: String) {
        val cleanValue = if (value.equals("kg", ignoreCase = true)) "kg" else "lbs"
        prefs.edit().putString(KEY_DEFAULT_UNIT, cleanValue).apply()
        _defaultUnit.value = cleanValue
    }

    private val _userName = MutableStateFlow(prefs.getString(KEY_USER_NAME, "").orEmpty())
    val userName: StateFlow<String> = _userName.asStateFlow()

    val isFirstLaunch: Boolean get() = !prefs.contains(KEY_USER_NAME)

    fun setUserName(value: String) {
        val clean = value.trim()
        prefs.edit().putString(KEY_USER_NAME, clean).apply()
        _userName.value = clean
    }

    private val _restDuration = MutableStateFlow(prefs.getInt(KEY_REST_DURATION, 90))
    val restDuration: StateFlow<Int> = _restDuration.asStateFlow()

    private val _usdaApiKey = MutableStateFlow(prefs.getString(KEY_USDA_API_KEY, "").orEmpty())
    val usdaApiKey: StateFlow<String> = _usdaApiKey.asStateFlow()

    fun setRestDuration(seconds: Int) {
        val clamped = seconds.coerceIn(30, 600)
        prefs.edit().putInt(KEY_REST_DURATION, clamped).apply()
        _restDuration.value = clamped
    }

    fun setUsdaApiKey(value: String) {
        val clean = value.trim()
        prefs.edit().putString(KEY_USDA_API_KEY, clean).apply()
        _usdaApiKey.value = clean
    }

    companion object {
        private const val KEY_LLM_ENDPOINT = "llm_endpoint"
        private const val KEY_DEFAULT_UNIT = "default_unit"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_REST_DURATION = "rest_duration"
        private const val KEY_USDA_API_KEY = "usda_api_key"
    }
}
