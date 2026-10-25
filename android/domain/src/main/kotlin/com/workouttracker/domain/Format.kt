package com.workouttracker.domain

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class WeightUnit(val label: String) { KG("kg"), LB("lb") }

object Units {
    private const val LB_PER_KG = 2.2046226218

    fun toDisplay(kg: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> kg
        WeightUnit.LB -> kg * LB_PER_KG
    }

    fun fromDisplay(value: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> value
        WeightUnit.LB -> value / LB_PER_KG
    }

    /** Step used by the +/- weight buttons, in the display unit. */
    fun step(unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> 2.5
        WeightUnit.LB -> 5.0
    }
}

object Format {
    /** 75 -> "1:15". Negative values clamp to 0. */
    fun clock(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }

    /** Milliseconds remaining -> "m:ss", rounding up so a timer never shows 0:00 early. */
    fun clockCeil(remainingMs: Long): String = clock(((remainingMs + 999) / 1000).toInt())

    /** Elapsed ms -> "12:34" or "1:02:03". */
    fun elapsed(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun minutes(ms: Long): String {
        val mins = (ms / 60_000.0).roundToInt()
        return if (mins < 60) "${mins}m" else "${mins / 60}h ${mins % 60}m"
    }

    fun weight(kg: Double, unit: WeightUnit): String {
        val v = Units.toDisplay(kg, unit)
        val rounded = (v * 10).roundToLong() / 10.0
        val text = if (abs(rounded - rounded.roundToLong()) < 0.05) rounded.roundToLong().toString() else "%.1f".format(rounded)
        return "$text ${unit.label}"
    }

    fun volume(kg: Double, unit: WeightUnit): String {
        val v = Units.toDisplay(kg, unit)
        return when {
            v >= 10_000 -> "%.1fk %s".format(v / 1000, unit.label)
            else -> "${v.roundToLong()} ${unit.label}"
        }
    }

    /** Human target for a plan item, e.g. "3 × 12" or "3 × 0:45". */
    fun target(item: PlanItem): String = when (item.type) {
        ExerciseType.REPS -> "${item.sets} × ${item.reps}"
        ExerciseType.DURATION -> "${item.sets} × ${clock(item.durationSec)}"
    }
}
