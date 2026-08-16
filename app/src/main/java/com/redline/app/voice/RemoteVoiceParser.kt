package com.redline.app.voice

import com.redline.app.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteVoiceParser @Inject constructor(
    private val settingsStore: SettingsStore
) {
    suspend fun parse(input: String): ParsedSet? = withContext(Dispatchers.IO) {
        val endpoint = settingsStore.llmEndpoint.value
        if (endpoint.isBlank()) return@withContext null

        runCatching {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val body = JSONObject()
                .put("schemaVersion", 1)
                .put("transcript", input)
                .toString()

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(body)
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()

            if (responseCode !in 200..299 || response.isBlank()) return@runCatching null
            parseResponse(JSONObject(response), input)
        }.getOrNull()
    }

    private fun parseResponse(json: JSONObject, rawInput: String): ParsedSet {
        val exerciseName = json.optString("exerciseName").takeIf { it.isNotBlank() } ?: "Unknown Exercise"
        val canonicalKey = json.optString("canonicalExerciseKey").takeIf { it.isNotBlank() }
            ?: ExerciseCatalog.match(exerciseName.lowercase())?.key

        return ParsedSet(
            exerciseName = ExerciseCatalog.displayNameForKey(canonicalKey) ?: exerciseName,
            canonicalExerciseKey = canonicalKey,
            weight = json.optNullableFloat("weight"),
            unit = json.optString("unit", "lbs").normalizeUnit(),
            reps = json.optNullableInt("reps"),
            rpe = json.optNullableFloat("rpe"),
            notes = json.optString("notes").takeIf { it.isNotBlank() },
            confidence = json.optDouble("confidence", 0.75).toFloat().coerceIn(0f, 1f),
            needsClarification = json.optBoolean("needsClarification", false),
            rawInput = rawInput
        )
    }

    private fun JSONObject.optNullableFloat(name: String): Float? {
        return if (isNull(name) || !has(name)) null else optDouble(name).toFloat()
    }

    private fun JSONObject.optNullableInt(name: String): Int? {
        return if (isNull(name) || !has(name)) null else optInt(name)
    }

    private fun String.normalizeUnit(): String {
        return if (startsWith("kg", ignoreCase = true) || startsWith("kilo", ignoreCase = true)) "kg" else "lbs"
    }
}
