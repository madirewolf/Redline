package com.redline.app.voice

object ExerciseCatalog {
    data class ExerciseAlias(
        val key: String,
        val displayName: String,
        val aliases: List<String>
    )

    val exercises = listOf(
        ExerciseAlias("bench_press", "Bench Press", listOf("bench press", "flat bench", "barbell bench", "bench")),
        ExerciseAlias("incline_bench_press", "Incline Bench Press", listOf("incline bench press", "incline barbell press", "incline bench")),
        ExerciseAlias("decline_bench_press", "Decline Bench Press", listOf("decline bench press", "decline bench")),
        ExerciseAlias("dumbbell_bench_press", "Dumbbell Bench Press", listOf("dumbbell bench press", "dumbbell bench", "dumbbell press", "db bench press", "db bench", "db press")),
        ExerciseAlias("incline_dumbbell_press", "Incline Dumbbell Press", listOf("incline dumbbell press", "incline dumbbell bench", "incline db press", "incline press")),
        ExerciseAlias("back_squat", "Back Squat", listOf("back squat", "barbell squat", "squat")),
        ExerciseAlias("front_squat", "Front Squat", listOf("front squat")),
        ExerciseAlias("goblet_squat", "Goblet Squat", listOf("goblet squat")),
        ExerciseAlias("bulgarian_split_squat", "Bulgarian Split Squat", listOf("bulgarian split squat", "split squat")),
        ExerciseAlias("lunge", "Lunge", listOf("walking lunge", "lunge")),
        ExerciseAlias("deadlift", "Deadlift", listOf("conventional deadlift", "deadlift")),
        ExerciseAlias("sumo_deadlift", "Sumo Deadlift", listOf("sumo deadlift", "sumo")),
        ExerciseAlias("romanian_deadlift", "Romanian Deadlift", listOf("romanian deadlift", "rdl")),
        ExerciseAlias("overhead_press", "Overhead Press", listOf("overhead press", "ohp", "shoulder press", "military press")),
        ExerciseAlias("dumbbell_shoulder_press", "Dumbbell Shoulder Press", listOf("dumbbell shoulder press", "db shoulder press", "seated dumbbell press")),
        ExerciseAlias("barbell_row", "Barbell Row", listOf("barbell row", "bent over row", "pendlay row", "row")),
        ExerciseAlias("dumbbell_row", "Dumbbell Row", listOf("dumbbell row", "db row", "one arm row", "single arm row")),
        ExerciseAlias("cable_row", "Seated Cable Row", listOf("seated cable row", "cable row", "seated row")),
        ExerciseAlias("pull_up", "Pull Up", listOf("pull up", "pullup")),
        ExerciseAlias("chin_up", "Chin Up", listOf("chin up", "chinup")),
        ExerciseAlias("lat_pulldown", "Lat Pulldown", listOf("lat pulldown", "lat pull down", "pulldown")),
        ExerciseAlias("bicep_curl", "Bicep Curl", listOf("bicep curl", "biceps curl", "dumbbell curl", "barbell curl", "db curl", "curl")),
        ExerciseAlias("hammer_curl", "Hammer Curl", listOf("hammer curl")),
        ExerciseAlias("tricep_extension", "Tricep Extension", listOf("tricep extension", "triceps extension", "skull crusher", "overhead extension")),
        ExerciseAlias("tricep_pushdown", "Tricep Pushdown", listOf("tricep pushdown", "triceps pushdown", "pushdown")),
        ExerciseAlias("leg_press", "Leg Press", listOf("leg press")),
        ExerciseAlias("leg_curl", "Leg Curl", listOf("hamstring curl", "leg curl")),
        ExerciseAlias("leg_extension", "Leg Extension", listOf("leg extension")),
        ExerciseAlias("hip_thrust", "Hip Thrust", listOf("hip thrust", "glute bridge")),
        ExerciseAlias("lateral_raise", "Lateral Raise", listOf("lateral raise", "side raise")),
        ExerciseAlias("face_pull", "Face Pull", listOf("face pull")),
        ExerciseAlias("rear_delt_fly", "Rear Delt Fly", listOf("rear delt fly", "reverse fly")),
        ExerciseAlias("cable_fly", "Cable Fly", listOf("cable fly", "chest fly", "pec fly")),
        ExerciseAlias("dumbbell_fly", "Dumbbell Fly", listOf("dumbbell fly", "db fly")),
        ExerciseAlias("dip", "Dip", listOf("dip")),
        ExerciseAlias("push_up", "Push Up", listOf("push up", "pushup")),
        ExerciseAlias("shrug", "Shrug", listOf("shrug")),
        ExerciseAlias("calf_raise", "Calf Raise", listOf("calf raise")),
        ExerciseAlias("plank", "Plank", listOf("plank")),
        ExerciseAlias("crunch", "Crunch", listOf("crunch")),
        ExerciseAlias("sit_up", "Sit Up", listOf("sit up", "situp"))
    )

    private val aliasPatterns: List<Pair<Regex, ExerciseAlias>> =
        exercises.flatMap { exercise -> exercise.aliases.map { it to exercise } }
            .sortedByDescending { it.first.length }
            .map { (alias, exercise) -> aliasRegex(alias) to exercise }

    /**
     * Longest alias wins, so "incline db press" beats "db press" and "bench".
     * Spoken plurals ("pull ups", "curls", "lunges") and hyphens ("push-ups")
     * match the singular alias.
     */
    fun match(input: String): ExerciseAlias? {
        val text = input.lowercase().replace('-', ' ')
        return aliasPatterns.firstOrNull { (pattern, _) -> pattern.containsMatchIn(text) }?.second
    }

    /**
     * The catalog entry a typed exercise name refers to, only when the whole
     * name is that exercise ("pull-ups", "Bench Press"). Unlike [match] it
     * won't read "Landmine Press" as some other press.
     */
    fun exact(name: String): ExerciseAlias? {
        val text = name.lowercase().replace('-', ' ').trim().replace(Regex("""\s+"""), " ")
        if (text.isEmpty()) return null
        return aliasPatterns.firstOrNull { (pattern, _) -> pattern.matchEntire(text) != null }?.second
    }

    fun displayNameForKey(key: String?): String? {
        if (key == null) return null
        return exercises.firstOrNull { it.key == key }?.displayName
    }

    /** Stable grouping key for a set: the catalog key, or the spoken name normalised. */
    fun groupingKey(canonicalKey: String?, exerciseName: String): String =
        canonicalKey ?: exerciseName.lowercase().trim()

    private fun aliasRegex(alias: String): Regex {
        // Each word may take a plural "s"/"es" ("pull ups", "skull crushers").
        val words = alias.split(' ').joinToString("""\s+""") { "${Regex.escape(it)}(?:e?s)?" }
        return Regex("""(?<![a-z0-9])$words(?![a-z0-9])""")
    }
}
