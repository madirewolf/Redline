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

    /** Kept in its own file so backup rules can leave it off the cloud and device transfers. */
    private val secrets = context.getSharedPreferences(SECRETS_FILE, Context.MODE_PRIVATE)

    private val _anthropicApiKey = MutableStateFlow(secrets.getString(KEY_ANTHROPIC_API_KEY, "").orEmpty())
    val anthropicApiKey: StateFlow<String> = _anthropicApiKey.asStateFlow()

    fun setAnthropicApiKey(value: String) {
        val clean = value.trim()
        secrets.edit().putString(KEY_ANTHROPIC_API_KEY, clean).apply()
        _anthropicApiKey.value = clean
    }

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

    /** True until the welcome dialog has been answered, even with a blank name. */
    private val _needsName = MutableStateFlow(!prefs.contains(KEY_USER_NAME))
    val needsName: StateFlow<Boolean> = _needsName.asStateFlow()

    fun setUserName(value: String) {
        val clean = value.trim()
        prefs.edit().putString(KEY_USER_NAME, clean).apply()
        _userName.value = clean
        _needsName.value = false
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
        const val SECRETS_FILE = "redline_secrets"
        private const val KEY_ANTHROPIC_API_KEY = "anthropic_api_key"
        private const val KEY_LLM_ENDPOINT = "llm_endpoint"
        private const val KEY_DEFAULT_UNIT = "default_unit"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_REST_DURATION = "rest_duration"
        private const val KEY_USDA_API_KEY = "usda_api_key"
    }
}
