package com.redline.app.voice

/**
 * The tier order, kept free of Android and Hilt so tests can drive it with fakes:
 * 1. structured "next" format, accepted at confidence >= 0.60
 * 2. Claude Haiku, when the user has added an Anthropic key and it read at least one set
 * 3. the remote endpoint, accepted at >= 0.55 and only when it is not asking for clarification
 * 4. the local parser, which always answers and splits "135 for 4 sets of 5 and then 122 for 10"
 */
class SetParsePipeline(
    private val remote: suspend (String) -> ParsedSet?,
    private val defaultUnit: () -> String,
    private val claude: suspend (String, ParseContext) -> List<ParsedSet>? = { _, _ -> null }
) {
    suspend fun parse(input: String): ParsedSet = parseAll(input).first()

    /** Every set in [input], in the order said. Never empty. */
    suspend fun parseAll(input: String, context: ParseContext = ParseContext()): List<ParsedSet> {
        val unit = defaultUnit()

        LocalSetParser.parseSeparated(input, unit)
            ?.takeIf { it.confidence >= SEPARATED_THRESHOLD }
            ?.let { return listOf(it) }

        claude(input, context)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        remote(input)
            ?.takeIf { it.confidence >= REMOTE_THRESHOLD && !it.needsClarification }
            ?.let { return listOf(it) }

        return LocalSetParser.parseMany(input, unit, context)
    }

    companion object {
        const val SEPARATED_THRESHOLD = 0.60f
        const val REMOTE_THRESHOLD = 0.55f
    }
}
