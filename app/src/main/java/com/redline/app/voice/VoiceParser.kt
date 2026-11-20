package com.redline.app.voice

import com.redline.app.settings.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/** Hilt entry point for set parsing. The tier logic lives in [SetParsePipeline]. */
@Singleton
class VoiceParser @Inject constructor(
    remoteVoiceParser: RemoteVoiceParser,
    claudeSetParser: ClaudeSetParser,
    private val settingsStore: SettingsStore
) {
    private val pipeline = SetParsePipeline(
        remote = { input -> remoteVoiceParser.parse(input) },
        defaultUnit = { settingsStore.defaultUnit.value },
        claude = { input, context -> claudeSetParser.parse(input, context) }
    )

    suspend fun parse(input: String): ParsedSet = pipeline.parse(input)

    suspend fun parseAll(input: String, context: ParseContext = ParseContext()): List<ParsedSet> =
        pipeline.parseAll(input, context)
}
