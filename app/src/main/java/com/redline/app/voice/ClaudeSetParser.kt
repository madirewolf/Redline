package com.redline.app.voice

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.ThinkingConfigDisabled
import com.fasterxml.jackson.core.JacksonException
import com.redline.app.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

class ClaudeParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Reads freeform set descriptions with Claude Haiku, using the Anthropic key the user
 * added in Settings. The key lives only on the phone; transcripts go to Anthropic only
 * when the "next" format didn't match, and each call is billed to the user's own account.
 */
@Singleton
class ClaudeSetParser @Inject constructor(
    private val settingsStore: SettingsStore
) {
    private val lock = Any()
    private var cachedKey: String? = null
    private var cachedClient: AnthropicClient? = null

    private val outputFormat: JsonOutputFormat by lazy {
        JsonOutputFormat.builder()
            .schema(
                JsonOutputFormat.Schema.builder()
                    .putAllAdditionalProperties(ClaudeSetPrompt.schema.mapValues { JsonValue.from(it.value) })
                    .build()
            )
            .build()
    }

    /** The sets Claude read, or null when no key is set or the call failed, so the caller falls back. */
    suspend fun parse(input: String, context: ParseContext): List<ParsedSet>? {
        val key = settingsStore.anthropicApiKey.value
        if (key.isBlank() || input.isBlank()) return null
        return try {
            request(key, input, context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Sends a sample transcript with [key], for the Settings test button. */
    suspend fun probe(key: String): Result<List<ParsedSet>> = try {
        Result.success(request(key.trim(), PROBE_TRANSCRIPT, ParseContext()))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(ClaudeParseException(friendlyMessage(e), e))
    }

    private suspend fun request(key: String, transcript: String, context: ParseContext): List<ParsedSet> =
        withContext(Dispatchers.IO) {
            val unit = settingsStore.defaultUnit.value
            val params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .system(ClaudeSetPrompt.system)
                .thinking(ThinkingConfigDisabled.builder().build())
                .outputConfig(
                    OutputConfig.builder()
                        .effort(OutputConfig.Effort.LOW)
                        .format(outputFormat)
                        .build()
                )
                .addUserMessage(ClaudeSetPrompt.userMessage(transcript, context, unit))
                .build()

            val message = client(key).messages().create(params)
            if (message.stopReason().orElse(null) == StopReason.REFUSAL) {
                throw ClaudeParseException("Claude declined to read that")
            }
            val text = message.content().joinToString("") { block -> block.text().map { it.text() }.orElse("") }
            ClaudeSetPrompt.parseResponse(text, transcript, context, unit)
        }

    private fun client(key: String): AnthropicClient = synchronized(lock) {
        cachedClient?.takeIf { cachedKey == key } ?: AnthropicOkHttpClient.builder()
            .apiKey(key)
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .maxRetries(1)
            .build()
            .also {
                cachedClient?.close()
                cachedClient = it
                cachedKey = key
            }
    }

    companion object {
        const val MODEL = "claude-haiku-5-5"
        const val MAX_TOKENS = 1024L
        const val TIMEOUT_SECONDS = 15L
        const val PROBE_TRANSCRIPT = "bench 135 for 4 sets of 5 and then 122 for 10"

        fun friendlyMessage(error: Throwable): String = when (error) {
            is ClaudeParseException -> error.message ?: "Claude couldn't read that"
            is AnthropicServiceException -> when (val code = error.statusCode()) {
                401 -> "Anthropic rejected that key. Copy it again from console.anthropic.com"
                403 -> "That key isn't allowed to use Claude Haiku"
                429 -> "Too many requests. Try again in a minute"
                400 -> if (error.message.orEmpty().contains("credit balance", ignoreCase = true)) {
                    "No API credits left on that Anthropic account"
                } else {
                    "Anthropic couldn't take that request (400)"
                }
                in 500..599 -> "Anthropic is having trouble right now. Try again shortly"
                else -> "Anthropic returned an error ($code)"
            }
            is AnthropicIoException -> "Couldn't reach Anthropic. Check your connection"
            is JacksonException, is IllegalArgumentException -> "Claude's answer wasn't in the expected shape"
            else -> error.message ?: "Claude couldn't read that"
        }
    }
}
