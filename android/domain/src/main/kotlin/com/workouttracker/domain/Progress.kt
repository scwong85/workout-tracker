package com.workouttracker.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class StatRange(val label: String, val days: Long?) {
    WEEK("7D", 7), MONTH("30D", 30), YEAR("1Y", 365), ALL("All", null),
}

data class Kpis(val workouts: Int, val activeMs: Long, val volumeKg: Double, val sets: Int) {
    companion object {
        val ZERO = Kpis(0, 0, 0.0, 0)
    }
}

data class Bucket(val label: String, val value: Int)

data class PrEvent(val at: Long, val sessionId: String, val text: String)

data class TrendPoint(val at: Long, val value: Double)

data class Overview(
    val range: StatRange,
    val current: Kpis,
    /** The equal-length period before [current]; null for "All". */
    val previous: Kpis?,
    val buckets: List<Bucket>,
    val muscleSplit: List<Pair<Category, Int>>,
)

object Progress {
    fun date(epochMs: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    private fun startOfDay(d: LocalDate, zone: ZoneId): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()

    fun kpis(sessions: List<Session>): Kpis = Kpis(
        workouts = sessions.size,
        activeMs = sessions.sumOf { it.durationMs },
        volumeKg = sessions.sumOf { Stats.volume(it.sets) },
        sets = sessions.sumOf { it.doneSets.size },
    )

    fun overview(
        sessions: List<Session>,
        categories: Map<String, Category>,
        range: StatRange,
        today: LocalDate,
        zone: ZoneId,
    ): Overview {
        val days = range.days
        val end = startOfDay(today.plusDays(1), zone)
        val (inRange, previous) = if (days == null) {
            sessions to null
        } else {
            val start = startOfDay(today.minusDays(days - 1), zone)
            val prevStart = startOfDay(today.minusDays(2 * days - 1), zone)
            sessions.filter { it.startedAt in start until end } to sessions.filter { it.startedAt in prevStart until start }
        }
        val split = inRange.flatMap { it.doneSets }
            .groupingBy { categories[it.exerciseId] ?: Category.FULL_BODY }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
        return Overview(
            range = range,
            current = kpis(inRange),
            previous = previous?.let(::kpis),
            buckets = buckets(inRange, range, today, zone, sessions),
            muscleSplit = split,
        )
    }

    /** Workouts per day (7D/30D), per month (1Y), or per month/year (All). */
    private fun buckets(inRange: List<Session>, range: StatRange, today: LocalDate, zone: ZoneId, all: List<Session>): List<Bucket> {
        val byDay = inRange.groupingBy { date(it.startedAt, zone) }.eachCount()
        return when (range) {
            StatRange.WEEK -> (6 downTo 0).map { back ->
                val d = today.minusDays(back.toLong())
                Bucket(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), byDay[d] ?: 0)
            }
            StatRange.MONTH -> (29 downTo 0).map { back ->
                val d = today.minusDays(back.toLong())
                Bucket(d.dayOfMonth.toString(), byDay[d] ?: 0)
            }
            StatRange.YEAR -> monthBuckets(inRange, YearMonth.from(today).minusMonths(11), YearMonth.from(today), zone)
            StatRange.ALL -> {
                val first = all.minOfOrNull { it.startedAt } ?: return emptyList()
                val from = YearMonth.from(date(first, zone))
                val to = YearMonth.from(today)
                if (ChronoUnit.MONTHS.between(from, to) < 24) {
                    monthBuckets(inRange, from, to, zone)
                } else {
                    val byYear = inRange.groupingBy { date(it.startedAt, zone).year }.eachCount()
                    (from.year..to.year).map { Bucket(it.toString(), byYear[it] ?: 0) }
                }
            }
        }
    }

    private fun monthBuckets(sessions: List<Session>, from: YearMonth, to: YearMonth, zone: ZoneId): List<Bucket> {
        val byMonth = sessions.groupingBy { YearMonth.from(date(it.startedAt, zone)) }.eachCount()
        val out = mutableListOf<Bucket>()
        var m = from
        while (!m.isAfter(to)) {
            out += Bucket(m.month.getDisplayName(TextStyle.NARROW, Locale.getDefault()), byMonth[m] ?: 0)
            m = m.plusMonths(1)
        }
        return out
    }

    /** Consecutive days with a workout, ending today (or yesterday, so the streak survives until tonight). */
    fun streakDays(sessions: List<Session>, today: LocalDate, zone: ZoneId): Int {
        val days = sessions.map { date(it.startedAt, zone) }.toSet()
        var d = if (today in days) today else today.minusDays(1)
        var count = 0
        while (d in days) {
            count++
            d = d.minusDays(1)
        }
        return count
    }

    /** Monday-first flags for the current week. */
    fun thisWeek(sessions: List<Session>, today: LocalDate, zone: ZoneId): List<Boolean> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val days = sessions.map { date(it.startedAt, zone) }.toSet()
        return (0L until 7L).map { monday.plusDays(it) in days }
    }

    fun dayCounts(sessions: List<Session>, zone: ZoneId): Map<LocalDate, Int> =
        sessions.groupingBy { date(it.startedAt, zone) }.eachCount()

    /**
     * Best value per session for one exercise: heaviest weight if it's ever weighted,
     * otherwise most reps, or longest hold for timed exercises.
     */
    fun trend(sessions: List<Session>, exerciseId: String): List<TrendPoint> {
        val relevant = sessions.sortedBy { it.startedAt }
            .mapNotNull { s -> s.doneSets.filter { it.exerciseId == exerciseId }.takeIf { it.isNotEmpty() }?.let { s to it } }
        if (relevant.isEmpty()) return emptyList()
        val weighted = relevant.any { (_, sets) -> sets.any { it.weightKg > 0 } }
        val type = relevant.first().second.first().type
        return relevant.map { (s, sets) ->
            val v = when {
                weighted -> sets.maxOf { it.weightKg }
                type == ExerciseType.DURATION -> sets.maxOf { it.actualDurationSec }.toDouble()
                else -> sets.maxOf { it.actualReps }.toDouble()
            }
            TrendPoint(s.startedAt, v)
        }
    }

    /** Every personal best in order, newest last. Incremental, so it's linear in the number of sets. */
    fun prTimeline(sessions: List<Session>, unit: WeightUnit = WeightUnit.KG): List<PrEvent> {
        val best = mutableMapOf<String, ExerciseRecord>()
        val events = mutableListOf<PrEvent>()
        for (s in sessions.sortedBy { it.startedAt }) {
            val history = best.values.map { rec ->
                // Re-wrap the running bests as a pseudo-session so Records.newRecords can compare.
                SessionSet(
                    id = "", exerciseId = rec.exerciseId, exerciseName = "", type = ExerciseType.REPS,
                    groupOrdinal = 0, setOrdinal = 0, targetReps = 0, actualReps = rec.maxReps,
                    targetDurationSec = 0, actualDurationSec = rec.maxDurationSec, weightKg = rec.maxWeightKg,
                    outcome = SetOutcome.DONE, completedAt = 0,
                )
            }
            val pseudo = Session(id = "", planId = null, planName = "", startedAt = 0, endedAt = 0, status = SessionStatus.COMPLETED, sets = history)
            Records.newRecords(listOf(pseudo), s.sets, unit).forEach { events += PrEvent(s.startedAt, s.id, it) }
            Records.of(s.sets).forEach { (id, rec) ->
                val prev = best[id]
                best[id] = if (prev == null) rec else ExerciseRecord(
                    exerciseId = id,
                    maxReps = maxOf(prev.maxReps, rec.maxReps),
                    maxWeightKg = maxOf(prev.maxWeightKg, rec.maxWeightKg),
                    maxDurationSec = maxOf(prev.maxDurationSec, rec.maxDurationSec),
                    maxSetVolume = maxOf(prev.maxSetVolume, rec.maxSetVolume),
                )
            }
        }
        return events
    }

    /** Relative change, e.g. 0.25 for +25%; null when there's nothing to compare against. */
    fun delta(current: Double, previous: Double): Double? = if (previous <= 0.0) null else (current - previous) / previous
}
