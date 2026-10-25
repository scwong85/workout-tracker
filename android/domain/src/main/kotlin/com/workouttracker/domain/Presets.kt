package com.workouttracker.domain

import com.workouttracker.domain.Category.ARMS
import com.workouttracker.domain.Category.BACK
import com.workouttracker.domain.Category.CHEST
import com.workouttracker.domain.Category.CORE
import com.workouttracker.domain.Category.FULL_BODY
import com.workouttracker.domain.Category.LEGS
import com.workouttracker.domain.Category.SHOULDERS
import com.workouttracker.domain.ExerciseType.DURATION
import com.workouttracker.domain.ExerciseType.REPS

/** Built-in exercise catalogue, carried over from the web app. IDs are stable so presets can be matched on import. */
object Presets {
    fun presetId(name: String): String =
        "preset-" + name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun p(name: String, type: ExerciseType, category: Category) =
        Exercise(id = presetId(name), name = name, type = type, category = category, isPreset = true)

    val exercises: List<Exercise> = listOf(
        p("Standard Push-ups", REPS, CHEST),
        p("Wide Push-ups", REPS, CHEST),
        p("Diamond Push-ups", REPS, CHEST),
        p("Decline Push-ups", REPS, CHEST),
        p("Pike Push-ups", REPS, SHOULDERS),
        p("Pseudo Planche Push-ups", REPS, CHEST),
        p("Archer Push-ups", REPS, CHEST),
        p("One-Arm Push-ups (Assisted)", REPS, CHEST),
        p("Clapping Push-ups", REPS, CHEST),
        p("Hindu Push-ups", REPS, FULL_BODY),
        p("Handstand Push-ups (Wall)", REPS, SHOULDERS),
        p("Dips (Parallel Bars)", REPS, CHEST),
        p("Bench Dips", REPS, ARMS),
        p("Korean Dips", REPS, ARMS),
        p("Pull-ups (Overhand)", REPS, BACK),
        p("Chin-ups (Underhand)", REPS, BACK),
        p("Wide Grip Pull-ups", REPS, BACK),
        p("Close Grip Pull-ups", REPS, BACK),
        p("Neutral Grip Pull-ups", REPS, BACK),
        p("Archer Pull-ups", REPS, BACK),
        p("Typewriter Pull-ups", REPS, BACK),
        p("Australian Pull-ups (Inverted Rows)", REPS, BACK),
        p("One-Arm Pull-ups (Assisted)", REPS, BACK),
        p("Muscle-ups", REPS, FULL_BODY),
        p("Commando Pull-ups", REPS, BACK),
        p("Plank (Front)", DURATION, CORE),
        p("Side Plank (Left)", DURATION, CORE),
        p("Side Plank (Right)", DURATION, CORE),
        p("Hollow Body Hold", DURATION, CORE),
        p("Crunches", REPS, CORE),
        p("Bicycle Crunches", REPS, CORE),
        p("Russian Twists", REPS, CORE),
        p("Leg Raises (Lying)", REPS, CORE),
        p("Hanging Leg Raises", REPS, CORE),
        p("Hanging Knee Raises", REPS, CORE),
        p("Dragon Flags", REPS, CORE),
        p("L-Sit Hold", DURATION, CORE),
        p("V-Sit Hold", DURATION, CORE),
        p("Ab Wheel Rollouts", REPS, CORE),
        p("Mountain Climbers", REPS, FULL_BODY),
        p("Bodyweight Squats", REPS, LEGS),
        p("Jump Squats", REPS, LEGS),
        p("Pistol Squats", REPS, LEGS),
        p("Bulgarian Split Squats", REPS, LEGS),
        p("Lunges (Forward)", REPS, LEGS),
        p("Reverse Lunges", REPS, LEGS),
        p("Side Lunges", REPS, LEGS),
        p("Calf Raises", REPS, LEGS),
        p("Single-Leg Calf Raises", REPS, LEGS),
        p("Glute Bridges", REPS, LEGS),
        p("Single-Leg Glute Bridges", REPS, LEGS),
        p("Nordic Curls", REPS, LEGS),
        p("Box Jumps", REPS, LEGS),
        p("Broad Jumps", REPS, LEGS),
        p("Burpees", REPS, FULL_BODY),
        p("Jump Rope", DURATION, FULL_BODY),
        p("High Knees", DURATION, FULL_BODY),
        p("Butt Kicks", DURATION, FULL_BODY),
        p("Bear Crawls", DURATION, FULL_BODY),
        p("Crab Walks", DURATION, FULL_BODY),
    )
}
