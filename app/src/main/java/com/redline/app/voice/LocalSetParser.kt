package com.redline.app.voice

/**
 * The two on-device parse tiers: the structured "next"-separated format and the
 * freeform fallback. Pure Kotlin with no Android types, so every rule is unit tested.
 *
 * Both tiers share one field extractor. It takes fields out of the text in a fixed
 * order (RPE, weight with a unit, reps, "at"/"with" weight) and blanks each match,
 * so a number is never claimed twice. Numbers nobody claimed are assigned by
 * position: weight first, then reps, which is the order people say a set in.
 */
object LocalSetParser {
    const val UNKNOWN_EXERCISE = "Unknown Exercise"

    private const val NUM = """\d+(?:\.\d+)?"""
    private const val UNIT = """lbs?|pounds?|kgs?|kilos?|kilograms?"""

    private val separatorPattern = Regex("""(?<![a-z])next(?![a-z])""")
    private val rpePattern = Regex("""(?<![a-z])(?:rpe|effort)\s*(?:of\s+|at\s+)?($NUM)(?![\d.])""")
    private val explicitWeightPattern = Regex("""(?<![a-z0-9.])($NUM)\s*($UNIT)(?![a-z])""")
    private val repsPattern = Regex("""(?<![a-z0-9.])(\d+)\s*(?:reps?|times?)(?![a-z])""")
    private val repsAfterForPattern = Regex("""(?<![a-z0-9])(?:for|x)\s*(\d+)(?![\d.a-z])(?:\s*(?:reps?|times?)(?![a-z]))?""")
    private val atWeightPattern = Regex("""(?<![a-z])(?:at|with)\s+($NUM)(?![\d.a-z])""")
    private val numberPattern = Regex("""(?<![a-z0-9.])$NUM(?![a-z0-9.])""")

    // "and then 122 for 10", "then 145 for 3", "135 for 5 and 145 for 3"
    private val segmentPattern = Regex("""(?<![a-z])(?:and then|then|after that|followed by|and(?=\s+\d))(?![a-z])""")
    // "4 sets of 5", "3 sets x 8 reps", or a bare "4 sets" with the reps said elsewhere
    private val setCountPattern = Regex("""(?<![a-z0-9.])(\d+)\s*sets?(?![a-z])(?:\s*(?:of|x)\s*(\d+)(?:\s*(?:reps?|times?)(?![a-z]))?)?""")
    const val MAX_SET_COUNT = 10

    private val hardKeywords = listOf("heavy", "hard", "tough", "struggled", "grind", "grinding", "maxed", "barely")
    private val easyKeywords = listOf("easy", "light", "smooth", "fast", "flew up", "warmup", "warm up")
    private val noteIndicators = Regex("""(?<![a-z])(felt|was|kind of|pretty|really|last|barely)(?![a-z])""")

    private val digitWords = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine")
    private val teenWords = listOf("ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
    private val tensWords = listOf("twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    // Plates are said as "two twenty five" or "three fifteen", not "two hundred and...".
    private val spokenHundredsPattern = Regex(
        """(?<![a-z])(${digitWords.joinToString("|")})\s+(?:(${teenWords.joinToString("|")})|(${tensWords.joinToString("|")})(?:\s+(${digitWords.joinToString("|")}))?)(?![a-z])"""
    )
    private val tensPattern = Regex(
        """(?<![a-z])(${tensWords.joinToString("|")})(?:\s+(${digitWords.joinToString("|")}))?(?![a-z])"""
    )
    private val smallNumberPattern = Regex(
        """(?<![a-z])(${(teenWords + digitWords.drop(1)).joinToString("|")})(?![a-z])"""
    )
    private val oneRepPattern = Regex("""(?<![a-z])one(?=\s+(?:reps?|times?)(?![a-z]))""")

    /**
     * Structured format: "{exercise} next {weight} next {reps} [next {notes}]".
     * Returns null when the input has no separator, or when the segments held
     * neither a weight nor reps, so the pipeline moves on to the next tier.
     */
    fun parseSeparated(input: String, defaultUnit: String): ParsedSet? {
        val text = normalize(input)
        val segments = text.split(separatorPattern).map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.size < 2) return null

        val fields = Fields()
        val exerciseText = extract(segments.first(), fields)
        val notes = segments.drop(1)
            .map { extract(it, fields) }
            .filter { it.isNotBlank() }
        fields.assignBareNumbers()
        if (fields.weight == null && fields.reps == null) return null

        val exercise = ExerciseCatalog.match(exerciseText)
        val exerciseName = exercise?.displayName
            ?: exerciseText.toTitleCase().ifBlank { UNKNOWN_EXERCISE }

        return ParsedSet(
            exerciseName = exerciseName,
            canonicalExerciseKey = exercise?.key,
            weight = fields.weight,
            unit = fields.unit ?: defaultUnit,
            reps = fields.reps,
            rpe = fields.rpe ?: inferRpe(text),
            notes = notes.joinToString(", ").takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() },
            confidence = scoreConfidence(exercise != null, fields.weight != null, fields.reps != null) + 0.1f,
            needsClarification = fields.reps == null || exerciseName == UNKNOWN_EXERCISE,
            rawInput = input,
            source = ParseSource.SEPARATED
        )
    }

    /** Freeform fallback for anything the other tiers did not accept. Always answers. */
    fun parseFreeform(input: String, defaultUnit: String): ParsedSet {
        val text = normalize(input)
        val exercise = ExerciseCatalog.match(text)
        val fields = Fields()
        extract(text, fields)
        fields.assignBareNumbers()

        return ParsedSet(
            exerciseName = exercise?.displayName ?: UNKNOWN_EXERCISE,
            canonicalExerciseKey = exercise?.key,
            weight = fields.weight,
            unit = fields.unit ?: defaultUnit,
            reps = fields.reps,
            rpe = fields.rpe ?: inferRpe(text),
            notes = extractNotes(input),
            confidence = scoreConfidence(exercise != null, fields.weight != null, fields.reps != null),
            needsClarification = exercise == null || fields.reps == null,
            rawInput = input,
            source = ParseSource.LOCAL
        )
    }

    /**
     * Freeform speech that may hold several sets: "135 for 4 sets of 5 and then 122 for 10".
     * Splits on "then" (and on "and" before a number), expands "N sets of R" into N sets,
     * and lets a part with no exercise continue the one before it, or the last one logged.
     * A plain single set comes out exactly as [parseFreeform] reads it.
     */
    fun parseMany(input: String, defaultUnit: String, context: ParseContext = ParseContext()): List<ParsedSet> {
        val text = normalize(input)
        val segments = text.split(segmentPattern)
            .map { it.trim() }
            .filter { numberPattern.containsMatchIn(it) || ExerciseCatalog.match(it) != null }
        // Split only when every piece is a whole set, so "bench 135 and 5 reps" stays one set.
        if (segments.size > 1) {
            val parsed = parseSegments(segments, input, defaultUnit, context)
            if (parsed.all { (set, _) -> set.reps != null }) return parsed.flatMap { (set, count) -> List(count) { set } }
        }
        if (setCountPattern.containsMatchIn(text)) {
            return parseSegments(listOf(text), input, defaultUnit, context).flatMap { (set, count) -> List(count) { set } }
        }
        return listOf(continueExercise(parseFreeform(input, defaultUnit), context.lastExerciseName, context.lastExerciseKey))
    }

    /** Each segment as a set and how many times it was done; a segment with no exercise continues the one before. */
    private fun parseSegments(
        segments: List<String>,
        input: String,
        defaultUnit: String,
        context: ParseContext
    ): List<Pair<ParsedSet, Int>> {
        var carriedName = context.lastExerciseName
        var carriedKey = context.lastExerciseKey
        return segments.map { segment ->
            var count = 1
            val rewritten = setCountPattern.find(segment)?.let { m ->
                count = (m.groupValues[1].toIntOrNull() ?: 1).coerceIn(1, MAX_SET_COUNT)
                val reps = m.groupValues[2]
                segment.replaceRange(m.range, if (reps.isNotEmpty()) " $reps reps " else " ")
            } ?: segment
            val parsed = continueExercise(parseFreeform(rewritten, defaultUnit), carriedName, carriedKey)
                .copy(rawInput = input)
            if (parsed.hasKnownExercise) {
                carriedName = parsed.exerciseName
                carriedKey = parsed.canonicalExerciseKey
            }
            parsed to count
        }
    }

    /** Gives a set with no exercise the one it most likely continues. */
    private fun continueExercise(set: ParsedSet, name: String?, key: String?): ParsedSet {
        if (set.hasKnownExercise || name.isNullOrBlank() || name == UNKNOWN_EXERCISE) return set
        return set.copy(
            exerciseName = name,
            canonicalExerciseKey = key,
            confidence = (set.confidence + 0.25f).coerceAtMost(1f),
            needsClarification = set.reps == null
        )
    }

    /**
     * Lowercases, turns spoken numbers into digits ("eight" -> 8, "two twenty five" -> 225),
     * splits "185x8" into "185 x 8", and drops punctuation the recogniser adds.
     */
    internal fun normalize(input: String): String {
        var text = input.lowercase()
            .replace('-', ' ')
            .replace(',', ' ')
            .replace(Regex("""\.(?!\d)"""), " ")
        text = spokenHundredsPattern.replace(text) { m ->
            val hundreds = (digitWords.indexOf(m.groupValues[1]) + 1) * 100
            val rest = when {
                m.groupValues[2].isNotEmpty() -> teenWords.indexOf(m.groupValues[2]) + 10
                else -> (tensWords.indexOf(m.groupValues[3]) + 2) * 10 +
                    (m.groupValues[4].takeIf { it.isNotEmpty() }?.let { digitWords.indexOf(it) + 1 } ?: 0)
            }
            (hundreds + rest).toString()
        }
        text = tensPattern.replace(text) { m ->
            val tens = (tensWords.indexOf(m.groupValues[1]) + 2) * 10
            val ones = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { digitWords.indexOf(it) + 1 } ?: 0
            (tens + ones).toString()
        }
        text = smallNumberPattern.replace(text) { m ->
            val word = m.groupValues[1]
            val teen = teenWords.indexOf(word)
            (if (teen >= 0) teen + 10 else digitWords.indexOf(word) + 1).toString()
        }
        text = oneRepPattern.replace(text, "1")
        text = Regex("""(\d)\s*x\s*(\d)""").replace(text, "$1 x $2")
        return text.collapseSpaces()
    }

    private class Fields {
        var weight: Float? = null
        var unit: String? = null
        var reps: Int? = null
        var rpe: Float? = null
        val bareNumbers = mutableListOf<Float>()

        /**
         * Unclaimed numbers in spoken order. Two or more: weight then reps.
         * One on its own: a small whole number is reps, anything else is weight.
         */
        fun assignBareNumbers() {
            val numbers = bareNumbers
            when {
                weight == null && reps == null && numbers.size >= 2 -> {
                    val (first, second) = numbers
                    when {
                        second.asReps() != null -> { weight = first; reps = second.asReps() }
                        first.asReps() != null -> { weight = second; reps = first.asReps() }
                        else -> weight = first
                    }
                }
                weight == null && reps == null && numbers.size == 1 -> {
                    val only = numbers.first()
                    if (only <= 20f && only.asReps() != null) reps = only.asReps() else weight = only
                }
                weight == null -> weight = numbers.firstOrNull()
                reps == null -> reps = numbers.firstNotNullOfOrNull { it.asReps() }
            }
        }
    }

    /** Takes every recognised field out of [segment] into [fields] and returns the words left over. */
    private fun extract(segment: String, fields: Fields): String {
        var text = segment

        fun take(pattern: Regex, onMatch: (MatchResult) -> Unit): Boolean {
            val match = pattern.find(text) ?: return false
            onMatch(match)
            text = text.replaceRange(match.range, " ")
            return true
        }

        take(rpePattern) { m ->
            if (fields.rpe == null) fields.rpe = m.groupValues[1].toFloatOrNull()?.coerceIn(1f, 10f)
        }
        take(explicitWeightPattern) { m ->
            if (fields.weight == null) {
                fields.weight = m.groupValues[1].toFloatOrNull()
                fields.unit = normalizeUnit(m.groupValues[2])
            }
        }
        val repsHandler: (MatchResult) -> Unit = { m ->
            if (fields.reps == null) fields.reps = m.groupValues[1].toIntOrNull()
        }
        if (!take(repsPattern, repsHandler)) take(repsAfterForPattern, repsHandler)
        take(atWeightPattern) { m ->
            if (fields.weight == null) fields.weight = m.groupValues[1].toFloatOrNull()
        }

        numberPattern.findAll(text).forEach { m -> m.value.toFloatOrNull()?.let(fields.bareNumbers::add) }
        text = numberPattern.replace(text, " ")
        return text.collapseSpaces()
    }

    private fun inferRpe(text: String): Float? {
        if (hardKeywords.any { text.containsWord(it) }) return 9f
        if (easyKeywords.any { text.containsWord(it) }) return 6f
        return null
    }

    private fun extractNotes(input: String): String? {
        val match = noteIndicators.find(input.lowercase()) ?: return null
        if (match.range.first == 0) return null
        val note = input.substring(match.range.first).trim().trimEnd('.', ',', '!', '?')
        return note.takeIf { it.length > 3 }?.replaceFirstChar { it.uppercase() }
    }

    private fun scoreConfidence(hasExercise: Boolean, hasWeight: Boolean, hasReps: Boolean): Float {
        var score = 0.2f
        if (hasExercise) score += 0.35f
        if (hasWeight) score += 0.2f
        if (hasReps) score += 0.25f
        return score.coerceIn(0f, 1f)
    }

    fun normalizeUnit(unit: String): String =
        if (unit.startsWith("kg", ignoreCase = true) || unit.startsWith("kilo", ignoreCase = true)) "kg" else "lbs"

    private fun Float.asReps(): Int? =
        if (this >= 1f && this <= 100f && this == this.toInt().toFloat()) toInt() else null

    private fun String.containsWord(word: String): Boolean =
        Regex("""(?<![a-z])${Regex.escape(word)}(?![a-z])""").containsMatchIn(this)

    private fun String.collapseSpaces(): String = trim().replace(Regex("""\s+"""), " ")

    private fun String.toTitleCase(): String =
        split(' ').filter { it.isNotBlank() }.joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
}
