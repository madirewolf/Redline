package com.redline.app.voice

import com.redline.app.settings.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/** Hilt entry point for set parsing. The tier logic lives in [SetParsePipeline]. */
@Singleton
class VoiceParser @Inject constructor(
    remoteVoiceParser: RemoteVoiceParser,
    private val settingsStore: SettingsStore
) {
    private val pipeline = SetParsePipeline(
        remote = { input -> remoteVoiceParser.parse(input) },
        defaultUnit = { settingsStore.defaultUnit.value }
    )

    suspend fun parse(input: String): ParsedSet = pipeline.parse(input)
}
