package com.redline.app.voice

/**
 * A set as typed into the editor: every field is the raw text, so the form
 * can show what's wrong with it instead of silently dropping it.
 */
data class SetDraft(
    val exercise: String = "",
    val weight: String = "",
    val unit: String = "lbs",
    val reps: String = "",
    val rpe: String = "",
    val notes: String = ""
) {
    val exerciseError: String?
        get() = when {
            exercise.isBlank() -> "Name the exercise"
            exercise.trim().equals(LocalSetParser.UNKNOWN_EXERCISE, ignoreCase = true) -> "Name the exercise"
            else -> null
        }

    val weightError: String?
        get() {
            if (weight.isBlank()) return null
            val value = weight.trim().toFloatOrNull() ?: return "Weight must be a number"
            return if (value < 0f || value > MAX_WEIGHT) "Weight looks wrong" else null
        }

    val repsError: String?
        get() {
            val value = reps.trim().toIntOrNull() ?: return "Reps must be a whole number"
            return if (value < 1 || value > MAX_REPS) "Reps must be 1 to $MAX_REPS" else null
        }

    val rpeError: String?
        get() {
            if (rpe.isBlank()) return null
            val value = rpe.trim().toFloatOrNull() ?: return "RPE must be a number"
            return if (value < 1f || value > 10f) "RPE runs from 1 to 10" else null
        }

    val isValid: Boolean
        get() = exerciseError == null && weightError == null && repsError == null && rpeError == null

    /**
     * The saved set. A name that is a catalog exercise gets its key and
     * proper spelling, so "pull-ups" typed by hand lands on the same
     * history row as "pull ups" said aloud.
     */
    fun toParsedSet(rawInput: String, source: ParseSource = ParseSource.MANUAL): ParsedSet? {
        if (!isValid) return null
        val name = exercise.trim().replace(Regex("""\s+"""), " ")
        val catalog = ExerciseCatalog.exact(name)
        return ParsedSet(
            exerciseName = catalog?.displayName ?: name,
            canonicalExerciseKey = catalog?.key,
            weight = weight.trim().toFloatOrNull(),
            unit = LocalSetParser.normalizeUnit(unit),
            reps = reps.trim().toInt(),
            rpe = rpe.trim().toFloatOrNull(),
            notes = notes.trim().ifEmpty { null },
            confidence = 1f,
            needsClarification = false,
            rawInput = rawInput,
            source = source
        )
    }

    companion object {
        const val MAX_WEIGHT = 2000f
        const val MAX_REPS = 999

        /** Pre-fill the editor from whatever was heard. Unknown names start blank. */
        fun from(parsed: ParsedSet) = SetDraft(
            exercise = if (parsed.hasKnownExercise) parsed.exerciseName else "",
            weight = parsed.weight?.let(::formatNumber).orEmpty(),
            unit = parsed.unit,
            reps = parsed.reps?.toString().orEmpty(),
            rpe = parsed.rpe?.let(::formatNumber).orEmpty(),
            notes = parsed.notes.orEmpty()
        )

        /** 185 rather than 185.0, 182.5 as is. */
        fun formatNumber(value: Float): String =
            if (value % 1f == 0f) value.toLong().toString() else value.toString()
    }
}
