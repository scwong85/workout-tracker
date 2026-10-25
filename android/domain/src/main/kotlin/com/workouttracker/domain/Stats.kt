package com.workouttracker.domain

object Stats {
    /** Σ weight × reps over completed rep sets. Timed and bodyweight sets add nothing. */
    fun volume(sets: List<SessionSet>): Double =
        sets.filter { it.outcome == SetOutcome.DONE && it.type == ExerciseType.REPS }.sumOf { it.weightKg * it.actualReps }
}

/** Best-ever values for one exercise. */
data class ExerciseRecord(
    val exerciseId: String,
    val maxReps: Int = 0,
    val maxWeightKg: Double = 0.0,
    val maxDurationSec: Int = 0,
    val maxSetVolume: Double = 0.0,
)

object Records {
    fun of(sets: List<SessionSet>): Map<String, ExerciseRecord> =
        sets.filter { it.outcome == SetOutcome.DONE }.groupBy { it.exerciseId }.mapValues { (id, list) ->
            ExerciseRecord(
                exerciseId = id,
                maxReps = list.maxOf { it.actualReps },
                maxWeightKg = list.maxOf { it.weightKg },
                maxDurationSec = list.maxOf { it.actualDurationSec },
                maxSetVolume = list.maxOf { it.weightKg * it.actualReps },
            )
        }

    /**
     * Human-readable PRs set by [current] compared with everything in [history]. An exercise
     * done for the first time isn't a record.
     */
    fun newRecords(history: List<Session>, current: List<SessionSet>, unit: WeightUnit = WeightUnit.KG): List<String> {
        val before = of(history.flatMap { it.sets })
        val now = of(current)
        val names = current.associate { it.exerciseId to it.exerciseName }
        return now.flatMap { (id, rec) ->
            val prev = before[id] ?: return@flatMap emptyList()
            val name = names[id].orEmpty()
            buildList {
                if (rec.maxWeightKg > prev.maxWeightKg && rec.maxWeightKg > 0) add("$name: ${Format.weight(rec.maxWeightKg, unit)}")
                else if (rec.maxReps > prev.maxReps && rec.maxReps > 0) add("$name: ${rec.maxReps} reps")
                if (rec.maxDurationSec > prev.maxDurationSec && rec.maxDurationSec > 0) add("$name: ${Format.clock(rec.maxDurationSec)} hold")
            }
        }
    }

    /** The best set from the most recent session that included [exerciseId]. */
    fun lastPerformance(history: List<Session>, exerciseId: String): SessionSet? =
        history.sortedByDescending { it.startedAt }
            .firstNotNullOfOrNull { s ->
                s.sets.filter { it.exerciseId == exerciseId && it.outcome == SetOutcome.DONE }
                    .maxWithOrNull(compareBy<SessionSet> { it.weightKg }.thenBy { it.actualReps }.thenBy { it.actualDurationSec })
            }

    fun describe(set: SessionSet, unit: WeightUnit): String {
        val main = when (set.type) {
            ExerciseType.REPS -> "${set.actualReps} reps"
            ExerciseType.DURATION -> Format.clock(set.actualDurationSec)
        }
        return if (set.weightKg > 0) "$main @ ${Format.weight(set.weightKg, unit)}" else main
    }
}
