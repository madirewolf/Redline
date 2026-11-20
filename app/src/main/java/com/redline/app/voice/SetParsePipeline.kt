package com.redline.app.voice

/**
 * The three-tier order, kept free of Android and Hilt so tests can drive it with fakes:
 * 1. structured "next" format, accepted at confidence >= 0.60
 * 2. the remote endpoint, accepted at >= 0.55 and only when it is not asking for clarification
 * 3. the freeform local parser, which always answers
 */
class SetParsePipeline(
    private val remote: suspend (String) -> ParsedSet?,
    private val defaultUnit: () -> String
) {
    suspend fun parse(input: String): ParsedSet {
        val unit = defaultUnit()

        LocalSetParser.parseSeparated(input, unit)
            ?.takeIf { it.confidence >= SEPARATED_THRESHOLD }
            ?.let { return it }

        remote(input)
            ?.takeIf { it.confidence >= REMOTE_THRESHOLD && !it.needsClarification }
            ?.let { return it }

        return LocalSetParser.parseFreeform(input, unit)
    }

    companion object {
        const val SEPARATED_THRESHOLD = 0.60f
        const val REMOTE_THRESHOLD = 0.55f
    }
}
