package com.redline.app.voice

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * The prompt, output schema and response reading for [ClaudeSetParser]. No network
 * and no Android types, so the parts that decide what gets logged are unit tested.
 */
object ClaudeSetPrompt {
    const val MAX_WEIGHT = 2000f
    const val MAX_REPS = 999

    /** Static, so every request shares it. */
    val system: String = buildString {
        appendLine("You turn what a lifter says between sets into set records for their workout log.")
        appendLine("The text is a speech-to-text transcript: expect misheard words, spoken numbers and filler.")
        appendLine()
        appendLine("Rules:")
        appendLine("- One entry per distinct set. Identical sets said with a count (\"4 sets of 5 at 135\", \"135 for 4 sets of 5\", \"3x8 at 225\") are one entry with that count.")
        appendLine("- weight is the load, reps the repetitions in each set. In \"135 for 5\" 135 is the weight. In \"4 sets of 5\" 4 is the count and 5 the reps.")
        appendLine("- An entry that names no exercise continues the entry before it. If the first entry names none, use the last exercise logged (given with the transcript), or \"\" if there is none.")
        appendLine("- Use the exercise's common gym name. Prefer a name from the known list below when it is the same movement.")
        appendLine("- unit: \"kg\" for kilos/kgs, \"lbs\" for pounds/lbs, otherwise the default unit given.")
        appendLine("- Plates mean 45 lb plates per side on a 45 lb bar (one plate 135, two 225, three 315), or 20 kg plates on a 20 kg bar when the unit is kg (one plate 60, two 100).")
        appendLine("- weight is null for bodyweight work or when no load is said.")
        appendLine("- rpe only when said (\"RPE 8\", \"8 out of 10\"). Reps in reserve convert: \"2 in the tank\" or \"RIR 2\" is rpe 8.")
        appendLine("- notes: a short note they add (\"paused\", \"with a belt\", \"left shoulder tight\"), else null.")
        appendLine("- Never invent a number that was not said. If the text does not describe a set, return an empty list.")
        appendLine()
        append("Known exercises: ")
        append(ExerciseCatalog.exercises.joinToString(", ") { it.displayName })
    }

    fun userMessage(transcript: String, context: ParseContext, defaultUnit: String): String = buildString {
        appendLine("Default unit: $defaultUnit")
        appendLine("Last exercise logged: ${context.lastExerciseName?.takeIf { it.isNotBlank() } ?: "none"}")
        appendLine("<transcript>")
        appendLine(transcript.trim())
        append("</transcript>")
    }

    /** JSON schema for structured output: every field required, nullable where it can be missing. */
    val schema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "sets" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "exercise" to mapOf("type" to "string"),
                        "count" to mapOf("type" to "integer", "description" to "How many identical sets"),
                        "weight" to nullable("number"),
                        "unit" to mapOf("type" to "string", "enum" to listOf("lbs", "kg")),
                        "reps" to nullable("integer"),
                        "rpe" to nullable("number"),
                        "notes" to nullable("string")
                    ),
                    "required" to listOf("exercise", "count", "weight", "unit", "reps", "rpe", "notes"),
                    "additionalProperties" to false
                )
            )
        ),
        "required" to listOf("sets"),
        "additionalProperties" to false
    )

    private fun nullable(type: String) = mapOf("anyOf" to listOf(mapOf("type" to type), mapOf("type" to "null")))

    private val mapper = ObjectMapper()

    /**
     * Reads the model's JSON into sets, one [ParsedSet] per set (a count of 4 becomes four),
     * matched to the exercise catalog and checked against the same limits as the editor.
     * Throws on text that is not the expected JSON.
     */
    fun parseResponse(json: String, rawInput: String, context: ParseContext, defaultUnit: String): List<ParsedSet> {
        val root = mapper.readTree(json)
        val entries = root.path("sets")
        require(entries.isArray) { "Response had no sets array" }

        var carriedName = context.lastExerciseName?.takeIf { it.isNotBlank() && it != LocalSetParser.UNKNOWN_EXERCISE }
        var carriedKey = context.lastExerciseKey
        val sets = mutableListOf<ParsedSet>()
        for (entry in entries) {
            val said = entry.path("exercise").asText("").trim()
            val match = said.takeIf { it.isNotEmpty() }?.let { ExerciseCatalog.exact(it) ?: ExerciseCatalog.match(it) }
            val name = when {
                match != null -> match.displayName
                said.isNotEmpty() -> said.toTitleCase()
                else -> carriedName
            }
            val key = when {
                match != null -> match.key
                said.isNotEmpty() -> null
                else -> carriedKey
            }
            if (name != null) {
                carriedName = name
                carriedKey = key
            }

            val weight = entry.floatOrNull("weight")?.takeIf { it > 0f && it <= MAX_WEIGHT }
            val reps = entry.intOrNull("reps")?.takeIf { it in 1..MAX_REPS }
            val unit = entry.path("unit").asText("").takeIf { it.isNotBlank() }
                ?.let(LocalSetParser::normalizeUnit) ?: defaultUnit
            val known = name != null
            val set = ParsedSet(
                exerciseName = name ?: LocalSetParser.UNKNOWN_EXERCISE,
                canonicalExerciseKey = key,
                weight = weight,
                unit = unit,
                reps = reps,
                rpe = entry.floatOrNull("rpe")?.coerceIn(1f, 10f),
                notes = entry.path("notes").takeUnless { it.isNull || it.isMissingNode }?.asText()?.trim()
                    ?.takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercase() },
                confidence = if (known && reps != null) 0.9f else 0.5f,
                needsClarification = !known || reps == null,
                rawInput = rawInput,
                source = ParseSource.CLAUDE
            )
            val count = (entry.intOrNull("count") ?: 1).coerceIn(1, LocalSetParser.MAX_SET_COUNT)
            repeat(count) { sets += set }
        }
        return sets
    }

    private fun JsonNode.floatOrNull(field: String): Float? =
        path(field).takeIf { it.isNumber }?.floatValue()?.takeIf { it.isFinite() }

    private fun JsonNode.intOrNull(field: String): Int? =
        path(field).takeIf { it.isNumber }?.let { node ->
            val value = node.doubleValue()
            if (value == Math.floor(value) && value in 0.0..1_000_000.0) value.toInt() else null
        }

    private fun String.toTitleCase(): String =
        split(' ').filter { it.isNotBlank() }.joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
}
