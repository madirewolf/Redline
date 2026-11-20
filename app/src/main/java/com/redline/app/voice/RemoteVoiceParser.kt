package com.redline.app.voice

import com.redline.app.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteVoiceParser @Inject constructor(
    private val settingsStore: SettingsStore
) {
    /** Tier two of the pipeline. Any failure is null, so a dead endpoint costs one timeout. */
    suspend fun parse(input: String): ParsedSet? {
        val endpoint = settingsStore.llmEndpoint.value.trim()
        if (endpoint.isBlank()) return null
        return request(endpoint, input).getOrNull()
    }

    /** Used by the Settings "Test" button: same request, but the failure reason comes back. */
    suspend fun probe(endpoint: String, transcript: String = PROBE_TRANSCRIPT): Result<ParsedSet> =
        request(endpoint.trim(), transcript)

    private suspend fun request(endpoint: String, input: String): Result<ParsedSet> =
        withContext(Dispatchers.IO) { requestBlocking(endpoint, input) }

    private fun requestBlocking(endpoint: String, input: String): Result<ParsedSet> {
        if (!isValidEndpoint(endpoint)) return failure("Enter a full http:// or https:// URL")
        val uri = URI(endpoint)
        var connection: HttpURLConnection? = null
        return try {
            connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val body = JSONObject()
                .put("schemaVersion", SCHEMA_VERSION)
                .put("transcript", input)
                .toString()
            OutputStreamWriter(connection.outputStream).use { it.write(body) }

            val code = connection.responseCode
            if (code !in 200..299) return failure("Endpoint answered HTTP $code")
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            if (response.isBlank()) return failure("Endpoint returned an empty body")
            Result.success(parseResponse(JSONObject(response), input))
        } catch (e: SocketTimeoutException) {
            failure("${uri.hostAndPort()} didn't answer in time")
        } catch (e: UnknownHostException) {
            failure("Could not find ${uri.host}")
        } catch (e: ConnectException) {
            failure("Nothing is listening at ${uri.hostAndPort()}")
        } catch (e: UnknownServiceException) {
            failure("Android blocks plain http:// here. Use an https:// endpoint.")
        } catch (e: JSONException) {
            failure("Response was not the expected JSON object")
        } catch (e: IOException) {
            failure(e.message ?: "Network error")
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseResponse(json: JSONObject, rawInput: String): ParsedSet {
        val exerciseName = json.optString("exerciseName").takeIf { it.isNotBlank() }
            ?: LocalSetParser.UNKNOWN_EXERCISE
        val canonicalKey = json.optString("canonicalExerciseKey").takeIf { it.isNotBlank() }
            ?: ExerciseCatalog.match(exerciseName)?.key
        val unit = json.optString("unit").takeIf { it.isNotBlank() }
            ?.let(LocalSetParser::normalizeUnit)
            ?: settingsStore.defaultUnit.value

        return ParsedSet(
            exerciseName = ExerciseCatalog.displayNameForKey(canonicalKey) ?: exerciseName,
            canonicalExerciseKey = canonicalKey,
            weight = json.optNullableFloat("weight"),
            unit = unit,
            reps = json.optNullableFloat("reps")?.toInt(),
            rpe = json.optNullableFloat("rpe")?.coerceIn(1f, 10f),
            notes = json.optString("notes").takeIf { it.isNotBlank() && it != "null" },
            confidence = json.optDouble("confidence", 0.75).toFloat().coerceIn(0f, 1f),
            needsClarification = json.optBoolean("needsClarification", false),
            rawInput = rawInput,
            source = ParseSource.REMOTE
        )
    }

    private fun JSONObject.optNullableFloat(name: String): Float? =
        if (!has(name) || isNull(name)) null else optDouble(name).takeIf { !it.isNaN() }?.toFloat()

    private fun URI.hostAndPort(): String = if (port > 0) "$host:$port" else host

    private fun <T> failure(message: String): Result<T> = Result.failure(IOException(message))

    companion object {
        const val SCHEMA_VERSION = 1
        const val PROBE_TRANSCRIPT = "bench press 185 for 8"
        private const val CONNECT_TIMEOUT_MS = 4_000
        private const val READ_TIMEOUT_MS = 8_000

        /** An absolute http(s) URL with a host. Anything else is rejected before a request is made. */
        fun isValidEndpoint(endpoint: String): Boolean {
            val uri = runCatching { URI(endpoint.trim()) }.getOrNull() ?: return false
            val scheme = uri.scheme?.lowercase()
            return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
        }
    }
}
