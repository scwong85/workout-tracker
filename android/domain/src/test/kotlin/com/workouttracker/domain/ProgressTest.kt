package com.workouttracker.domain

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProgressTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 25) // a Sunday
    private var n = 0

    private fun at(d: LocalDate, hour: Int = 9) = d.atTime(hour, 0).toInstant(zone).toEpochMilli()

    private fun set(ex: String, reps: Int = 10, weight: Double = 0.0, duration: Int = 0, type: ExerciseType = ExerciseType.REPS) = SessionSet(
        id = "s${n++}", exerciseId = ex, exerciseName = ex, type = type, groupOrdinal = 0, setOrdinal = 1,
        targetReps = reps, actualReps = reps, targetDurationSec = duration, actualDurationSec = duration,
        weightKg = weight, outcome = SetOutcome.DONE, completedAt = 0,
    )

    private fun session(d: LocalDate, minutes: Long = 30, vararg sets: SessionSet) = Session(
        id = "x${n++}", planId = null, planName = "P", startedAt = at(d), endedAt = at(d) + minutes * 60_000,
        status = SessionStatus.COMPLETED, sets = sets.toList(),
    )

    @Test fun overviewSplitsCurrentAndPreviousPeriods() {
        val sessions = listOf(
            session(today, 30, set("A", 10, 20.0)),
            session(today.minusDays(6), 30, set("A", 10, 20.0)),
            session(today.minusDays(7), 60, set("A", 5, 10.0)), // previous week
            session(today.minusDays(20), 60, set("A")), // outside both
        )
        val o = Progress.overview(sessions, mapOf("A" to Category.CHEST), StatRange.WEEK, today, zone)
        assertEquals(2, o.current.workouts)
        assertEquals(400.0, o.current.volumeKg)
        assertEquals(60 * 60_000L, o.current.activeMs)
        assertEquals(1, o.previous!!.workouts)
        assertEquals(7, o.buckets.size)
        assertEquals(1, o.buckets.last().value)
        assertEquals(1, o.buckets.first().value)
        assertEquals(listOf(Category.CHEST to 2), o.muscleSplit)
    }

    @Test fun allRangeHasNoPreviousAndMonthBuckets() {
        val sessions = listOf(session(today.minusMonths(3)), session(today))
        val o = Progress.overview(sessions, emptyMap(), StatRange.ALL, today, zone)
        assertNull(o.previous)
        assertEquals(4, o.buckets.size)
        assertEquals(2, o.buckets.sumOf { it.value })
    }

    @Test fun streakSurvivesUntilEndOfToday() {
        val sessions = listOf(session(today.minusDays(1)), session(today.minusDays(2)), session(today.minusDays(4)))
        assertEquals(2, Progress.streakDays(sessions, today, zone))
        assertEquals(3, Progress.streakDays(sessions + session(today), today, zone))
        assertEquals(0, Progress.streakDays(listOf(session(today.minusDays(3))), today, zone))
    }

    @Test fun thisWeekIsMondayFirst() {
        val week = Progress.thisWeek(listOf(session(LocalDate.of(2026, 10, 19)), session(today)), today, zone)
        assertEquals(listOf(true, false, false, false, false, false, true), week)
    }

    @Test fun trendUsesWeightWhenEverWeighted() {
        val sessions = listOf(
            session(today.minusDays(2), 30, set("A", 12)),
            session(today.minusDays(1), 30, set("A", 8, 10.0), set("A", 6, 12.5)),
        )
        assertEquals(listOf(0.0, 12.5), Progress.trend(sessions, "A").map { it.value })
        val holds = listOf(session(today, 30, set("P", 0, duration = 40, type = ExerciseType.DURATION)))
        assertEquals(listOf(40.0), Progress.trend(holds, "P").map { it.value })
    }

    @Test fun prTimelineFlagsImprovementsInOrder() {
        val sessions = listOf(
            session(today.minusDays(3), 30, set("A", 10)),
            session(today.minusDays(2), 30, set("A", 12)),
            session(today.minusDays(1), 30, set("A", 11)),
            session(today, 30, set("A", 12, 5.0)),
        )
        assertEquals(listOf("A: 12 reps", "A: 5 kg"), Progress.prTimeline(sessions).map { it.text })
    }

    @Test fun deltaIsNullWithoutBaseline() {
        assertNull(Progress.delta(5.0, 0.0))
        assertEquals(0.5, Progress.delta(3.0, 2.0))
    }
}
