package com.redline.app.voice

/** Which parse tier produced a set. Shown on the confirmation card so the tiering is visible. */
enum class ParseSource { SEPARATED, CLAUDE, REMOTE, LOCAL, MANUAL }

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
    val rawInput: String,
    val source: ParseSource = ParseSource.LOCAL
) {
    val hasKnownExercise: Boolean
        get() = exerciseName.isNotBlank() && exerciseName != LocalSetParser.UNKNOWN_EXERCISE

    /** A set can be saved once we know what was lifted and for how many reps. */
    val isComplete: Boolean
        get() = hasKnownExercise && reps != null && reps > 0
}

/**
 * What the parsers know beyond the words themselves. A set said without an
 * exercise ("then 122 for 10") continues the last one logged.
 */
data class ParseContext(
    val lastExerciseName: String? = null,
    val lastExerciseKey: String? = null
)
