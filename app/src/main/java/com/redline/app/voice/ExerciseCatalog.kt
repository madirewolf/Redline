package com.redline.app.voice

object ExerciseCatalog {
    data class ExerciseAlias(
        val key: String,
        val displayName: String,
        val aliases: List<String>
    )

    val exercises = listOf(
        ExerciseAlias("bench_press", "Bench Press", listOf("bench press", "flat bench", "bench")),
        ExerciseAlias("incline_bench_press", "Incline Bench Press", listOf("incline bench press", "incline bench")),
        ExerciseAlias("decline_bench_press", "Decline Bench Press", listOf("decline bench press", "decline bench")),
        ExerciseAlias("back_squat", "Back Squat", listOf("back squat", "squat")),
        ExerciseAlias("front_squat", "Front Squat", listOf("front squat")),
        ExerciseAlias("goblet_squat", "Goblet Squat", listOf("goblet squat")),
        ExerciseAlias("deadlift", "Deadlift", listOf("deadlift", "conventional deadlift")),
        ExerciseAlias("sumo_deadlift", "Sumo Deadlift", listOf("sumo deadlift", "sumo")),
        ExerciseAlias("romanian_deadlift", "Romanian Deadlift", listOf("romanian deadlift", "rdl")),
        ExerciseAlias("overhead_press", "Overhead Press", listOf("overhead press", "ohp", "shoulder press", "military press")),
        ExerciseAlias("barbell_row", "Barbell Row", listOf("barbell row", "bent over row", "pendlay row", "row")),
        ExerciseAlias("pull_up", "Pull Up", listOf("pull up", "pullup")),
        ExerciseAlias("chin_up", "Chin Up", listOf("chin up", "chinup")),
        ExerciseAlias("lat_pulldown", "Lat Pulldown", listOf("lat pulldown", "pulldown")),
        ExerciseAlias("bicep_curl", "Bicep Curl", listOf("bicep curl", "curl")),
        ExerciseAlias("hammer_curl", "Hammer Curl", listOf("hammer curl")),
        ExerciseAlias("tricep_extension", "Tricep Extension", listOf("tricep extension", "skull crusher", "tricep pushdown")),
        ExerciseAlias("leg_press", "Leg Press", listOf("leg press")),
        ExerciseAlias("leg_curl", "Leg Curl", listOf("leg curl")),
        ExerciseAlias("leg_extension", "Leg Extension", listOf("leg extension")),
        ExerciseAlias("hip_thrust", "Hip Thrust", listOf("hip thrust", "glute bridge")),
        ExerciseAlias("lateral_raise", "Lateral Raise", listOf("lateral raise")),
        ExerciseAlias("face_pull", "Face Pull", listOf("face pull")),
        ExerciseAlias("rear_delt_fly", "Rear Delt Fly", listOf("rear delt fly")),
        ExerciseAlias("cable_fly", "Cable Fly", listOf("cable fly", "chest fly")),
        ExerciseAlias("dumbbell_fly", "Dumbbell Fly", listOf("dumbbell fly")),
        ExerciseAlias("dip", "Dip", listOf("dip", "dips")),
        ExerciseAlias("push_up", "Push Up", listOf("push up", "pushup")),
        ExerciseAlias("shrug", "Shrug", listOf("shrug")),
        ExerciseAlias("calf_raise", "Calf Raise", listOf("calf raise")),
        ExerciseAlias("plank", "Plank", listOf("plank")),
        ExerciseAlias("crunch", "Crunch", listOf("crunch")),
        ExerciseAlias("sit_up", "Sit Up", listOf("sit up"))
    )

    val aliasesByLength: List<Pair<String, ExerciseAlias>> =
        exercises.flatMap { exercise -> exercise.aliases.map { it to exercise } }
            .sortedByDescending { it.first.length }

    fun match(input: String): ExerciseAlias? {
        return aliasesByLength.firstOrNull { (alias, _) ->
            input.containsWordPhrase(alias)
        }?.second
    }

    fun displayNameForKey(key: String?): String? {
        if (key == null) return null
        return exercises.firstOrNull { it.key == key }?.displayName
    }

    private fun String.containsWordPhrase(phrase: String): Boolean {
        val pattern = Regex("""(?<![a-z0-9])${Regex.escape(phrase)}(?![a-z0-9])""", RegexOption.IGNORE_CASE)
        return pattern.containsMatchIn(this)
    }
}
