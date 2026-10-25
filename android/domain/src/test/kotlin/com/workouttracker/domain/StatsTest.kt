package com.workouttracker.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatsTest {
    private var n = 0
    private fun set(
        ex: String, reps: Int = 10, weight: Double = 0.0, duration: Int = 0,
        type: ExerciseType = ExerciseType.REPS, outcome: SetOutcome = SetOutcome.DONE,
    ) = SessionSet(
        id = "s${n++}", exerciseId = ex, exerciseName = ex, type = type, groupOrdinal = 0, setOrdinal = 1,
        targetReps = reps, actualReps = reps, targetDurationSec = duration, actualDurationSec = duration,
        weightKg = weight, outcome = outcome, completedAt = 0,
    )

    private fun session(start: Long, vararg sets: SessionSet) =
        Session(id = "x$start", planId = null, planName = "P", startedAt = start, endedAt = start + 60_000, status = SessionStatus.COMPLETED, sets = sets.toList())

    @Test fun volumeIsWeightTimesRepsForDoneSetsOnly() {
        // Web app's "total weight" summed weights; volume multiplies by reps.
        val v = Stats.volume(listOf(set("A", 10, 20.0), set("A", 5, 30.0), set("A", 10, 50.0, outcome = SetOutcome.SKIPPED)))
        assertEquals(350.0, v)
    }

    @Test fun newRecordsComparesAgainstHistoryAndIgnoresFirstTimers() {
        val history = listOf(session(1, set("A", 10, 20.0), set("Plank", type = ExerciseType.DURATION, duration = 30)))
        val current = listOf(set("A", 8, 22.5), set("B", 12), set("Plank", type = ExerciseType.DURATION, duration = 45))
        val prs = Records.newRecords(history, current)
        assertEquals(listOf("A: 22.5 kg", "Plank: 0:45 hold"), prs)
    }

    @Test fun repPrOnlyWhenWeightDidNotImprove() {
        val history = listOf(session(1, set("A", 10)))
        assertEquals(listOf("A: 12 reps"), Records.newRecords(history, listOf(set("A", 12))))
        assertTrue(Records.newRecords(history, listOf(set("A", 9))).isEmpty())
    }

    @Test fun lastPerformanceUsesMostRecentSession() {
        val history = listOf(
            session(1, set("A", 15, 10.0)),
            session(2, set("A", 8, 20.0), set("A", 10, 20.0)),
        )
        val last = Records.lastPerformance(history, "A")!!
        assertEquals(10, last.actualReps)
        assertEquals("10 reps @ 20 kg", Records.describe(last, WeightUnit.KG))
        assertNull(Records.lastPerformance(history, "missing"))
    }
}
