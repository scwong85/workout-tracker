package com.workouttracker.domain

/** Starter plans offered on first run; built only from presets. */
object Templates {
    private fun item(name: String, sets: Int, reps: Int = 10, durationSec: Int = 30): PlanItem {
        val ex = Presets.exercises.first { it.name == name }
        return PlanDefaults.itemFor(ex).copy(sets = sets, reps = reps, durationSec = durationSec)
    }

    private fun single(name: String, sets: Int, reps: Int = 10, durationSec: Int = 30, rest: Int = 60) =
        PlanGroup(id = Ids.new(), items = listOf(item(name, sets, reps, durationSec)), restAfterSec = rest)

    private fun superset(rest: Int, vararg items: PlanItem) = PlanGroup(id = Ids.new(), items = items.toList(), restAfterSec = rest)

    fun all(now: Long): List<Plan> = listOf(
        Plan(
            id = Ids.new(), name = "Full Body Beginner", createdAt = now, updatedAt = now,
            groups = listOf(
                single("Bodyweight Squats", 3, reps = 15),
                single("Standard Push-ups", 3, reps = 10),
                single("Australian Pull-ups (Inverted Rows)", 3, reps = 10),
                single("Glute Bridges", 3, reps = 15),
                single("Plank (Front)", 3, durationSec = 30, rest = 45),
            ),
        ),
        Plan(
            id = Ids.new(), name = "Push Day", createdAt = now, updatedAt = now,
            groups = listOf(
                single("Dips (Parallel Bars)", 3, reps = 8, rest = 90),
                single("Pike Push-ups", 3, reps = 10),
                superset(60, item("Diamond Push-ups", 3, reps = 10), item("Wide Push-ups", 3, reps = 12)),
            ),
        ),
        Plan(
            id = Ids.new(), name = "Pull Day", createdAt = now, updatedAt = now,
            groups = listOf(
                single("Pull-ups (Overhand)", 4, reps = 6, rest = 120),
                single("Chin-ups (Underhand)", 3, reps = 8, rest = 90),
                single("Australian Pull-ups (Inverted Rows)", 3, reps = 12),
                single("Hanging Knee Raises", 3, reps = 12),
            ),
        ),
        Plan(
            id = Ids.new(), name = "Leg Day", createdAt = now, updatedAt = now,
            groups = listOf(
                single("Bulgarian Split Squats", 3, reps = 10, rest = 90),
                single("Reverse Lunges", 3, reps = 12),
                single("Single-Leg Glute Bridges", 3, reps = 12),
                single("Calf Raises", 3, reps = 20, rest = 45),
            ),
        ),
        Plan(
            id = Ids.new(), name = "Core 10", createdAt = now, updatedAt = now,
            groups = listOf(
                superset(
                    30,
                    item("Hollow Body Hold", 3, durationSec = 30),
                    item("Bicycle Crunches", 3, reps = 20),
                    item("Side Plank (Left)", 3, durationSec = 30),
                    item("Side Plank (Right)", 3, durationSec = 30),
                ),
            ),
        ),
    )
}
