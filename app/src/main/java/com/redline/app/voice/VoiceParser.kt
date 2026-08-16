package com.redline.app.voice

import com.redline.app.settings.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedSet(
    val exerciseName: String,
    val canonicalExerciseKey: String? = null,
    val weight: Float? = null,
    val unit: String = "lbs",
    val reps: Int? = null,
    val rpe: Float? = null,
    val notes: String? = null,
    val confidence: Float = 0.65f,
    val needsClarification: Boolean = false,
    val rawInput: String
)

@Singleton
class VoiceParser @Inject constructor(
    private val remoteVoiceParser: RemoteVoiceParser,
    private val settingsStore: SettingsStore
) {
    private val explicitWeightPattern = Regex(
        """(?<![a-z0-9])(\d+(?:\.\d+)?)\s*(lbs?|kg|pounds?|kilos?)(?![a-z0-9])""",
        RegexOption.IGNORE_CASE
    )
    private val atWeightPattern = Regex("""(?:at|with)\s+(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val repsAfterForPattern = Regex("""(?:for|x)\s+(\d+)(?:\s*(?:reps?|times?))?""", RegexOption.IGNORE_CASE)
    private val repsPattern = Regex("""(?<![a-z0-9])(\d+)\s*(?:reps?|times?)(?![a-z0-9])""", RegexOption.IGNORE_CASE)
    private val rpePattern = Regex("""(?:rpe|effort)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val numberPattern = Regex("""(?<![a-z0-9])\d+(?:\.\d+)?(?![a-z0-9])""", RegexOption.IGNORE_CASE)

    private val hardKeywords = listOf("heavy", "hard", "tough", "struggled", "grind", "grinding", "maxed", "barely")
    private val easyKeywords = listOf("easy", "light", "smooth", "fast", "flew up", "warmup", "warm up")

    suspend fun parse(input: String): ParsedSet {
        // Try separator-based parsing first (highest reliability)
        val separated = parseSeparated(input)
        if (separated != null && separated.confidence >= 0.6f) return separated

        // Try remote LLM parser
        val remote = remoteVoiceParser.parse(input)
        if (remote != null && remote.confidence >= 0.55f && !remote.needsClarification) {
            return remote
        }

        // Fallback to freeform local parsing
        return parseLocally(input)
    }

    /**
     * Structured voice format: "{exercise} next {weight} next {reps} [next {notes}]"
     * Splits on "next" separator, then parses each segment independently.
     */
    private fun parseSeparated(input: String): ParsedSet? {
        val lower = input.lowercase().trim()
        if (!lower.contains("next")) return null

        val segments = lower.split(Regex("""\bnext\b""")).map { it.trim() }.filter { it.isNotBlank() }
        if (segments.size < 2) return null

        // First segment is always the exercise
        val exerciseSegment = segments[0]
        val exercise = ExerciseCatalog.match(exerciseSegment)

        // Remaining segments: parse for weight, reps, rpe, notes
        var weight: Float? = null
        var unit: String? = null
        var reps: Int? = null
        var rpe: Float? = null
        var notes: String? = null

        for (i in 1 until segments.size) {
            val seg = segments[i]
            when {
                rpe == null && rpePattern.containsMatchIn(seg) -> {
                    rpe = rpePattern.find(seg)?.groupValues?.get(1)?.toFloatOrNull()?.coerceIn(1f, 10f)
                }
                reps == null && (repsPattern.containsMatchIn(seg) || seg.matches(Regex("""^\d+\s*(reps?|times?)?$"""))) -> {
                    reps = Regex("""\d+""").find(seg)?.value?.toIntOrNull()
                }
                weight == null && (explicitWeightPattern.containsMatchIn(seg) || seg.matches(Regex("""^\d+(?:\.\d+)?(?:\s*(lbs?|kg|pounds?|kilos?))?$"""))) -> {
                    val wMatch = Regex("""(\d+(?:\.\d+)?)""").find(seg)
                    weight = wMatch?.value?.toFloatOrNull()
                    val uMatch = Regex("""(lbs?|kg|pounds?|kilos?)""", RegexOption.IGNORE_CASE).find(seg)
                    unit = uMatch?.value?.normalizeUnit()
                }
                else -> {
                    notes = seg.replaceFirstChar { it.uppercase() }
                }
            }
        }

        // If we got reps but no weight, and the weight looks like reps, swap
        if (weight != null && reps == null && weight <= 20f) {
            reps = weight.toInt()
            weight = null
        }

        return ParsedSet(
            exerciseName = exercise?.displayName ?: exerciseSegment.replaceFirstChar { it.uppercase() },
            canonicalExerciseKey = exercise?.key,
            weight = weight,
            unit = unit ?: settingsStore.defaultUnit.value,
            reps = reps,
            rpe = rpe ?: findRpe(input.lowercase()),
            notes = notes,
            confidence = scoreConfidence(exercise != null, weight != null, reps != null) + 0.1f,
            needsClarification = false,
            rawInput = input
        )
    }

    fun parseLocally(input: String): ParsedSet {
        val lower = input.lowercase().trim()
        val exercise = ExerciseCatalog.match(lower)
        val reps = findReps(lower)
        val weightAndUnit = findWeight(lower, exercise, reps)
        val rpe = findRpe(lower)
        val notes = extractNotes(input)
        val missingCriticalFields = exercise == null || reps == null

        return ParsedSet(
            exerciseName = exercise?.displayName ?: "Unknown Exercise",
            canonicalExerciseKey = exercise?.key,
            weight = weightAndUnit.first,
            unit = weightAndUnit.second ?: settingsStore.defaultUnit.value,
            reps = reps,
            rpe = rpe,
            notes = notes,
            confidence = scoreConfidence(exercise != null, weightAndUnit.first != null, reps != null),
            needsClarification = missingCriticalFields,
            rawInput = input
        )
    }

    private fun findWeight(
        input: String,
        exercise: ExerciseCatalog.ExerciseAlias?,
        reps: Int?
    ): Pair<Float?, String?> {
        explicitWeightPattern.find(input)?.let { match ->
            return match.groupValues[1].toFloatOrNull() to match.groupValues[2].normalizeUnit()
        }

        atWeightPattern.find(input)?.let { match ->
            return match.groupValues[1].toFloatOrNull() to null
        }

        if (exercise != null) {
            for (alias in exercise.aliases.sortedByDescending { it.length }) {
                val beforeExercise = Regex("""(\d+(?:\.\d+)?)\s+${Regex.escape(alias)}""", RegexOption.IGNORE_CASE)
                beforeExercise.find(input)?.let { match ->
                    return match.groupValues[1].toFloatOrNull() to null
                }
            }
        }

        val numbers = numberPattern.findAll(input)
            .mapNotNull { it.value.toFloatOrNull() }
            .toList()

        val likelyWeight = numbers.firstOrNull { number ->
            reps == null || number.toInt() != reps || number >= 25f
        }

        return likelyWeight to null
    }

    private fun findReps(input: String): Int? {
        repsPattern.find(input)?.let { return it.groupValues[1].toIntOrNull() }
        repsAfterForPattern.find(input)?.let { return it.groupValues[1].toIntOrNull() }
        return null
    }

    private fun findRpe(input: String): Float? {
        rpePattern.find(input)?.let {
            return it.groupValues[1].toFloatOrNull()?.coerceIn(1f, 10f)
        }
        if (hardKeywords.any { input.contains(it) }) return 9f
        if (easyKeywords.any { input.contains(it) }) return 6f
        return null
    }

    private fun extractNotes(input: String): String? {
        val lower = input.lowercase()
        val noteIndicators = listOf("felt", "was", "kind of", "pretty", "really", "last", "first", "barely")
        for (indicator in noteIndicators) {
            val idx = lower.indexOf(indicator)
            if (idx > 0) {
                val note = input.substring(idx).trim()
                if (note.length > 3) return note.replaceFirstChar { it.uppercase() }
            }
        }
        return null
    }

    private fun scoreConfidence(hasExercise: Boolean, hasWeight: Boolean, hasReps: Boolean): Float {
        var score = 0.2f
        if (hasExercise) score += 0.35f
        if (hasWeight) score += 0.2f
        if (hasReps) score += 0.25f
        return score.coerceIn(0f, 1f)
    }

    private fun String.normalizeUnit(): String {
        return if (startsWith("kg", ignoreCase = true) || startsWith("kilo", ignoreCase = true)) "kg" else "lbs"
    }
}
