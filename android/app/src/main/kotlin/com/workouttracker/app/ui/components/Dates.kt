package com.workouttracker.app.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

fun epochToDate(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

/** "today", "yesterday", "3 days ago", or a short date. */
fun relativeDay(epochMs: Long, today: LocalDate = LocalDate.now()): String {
    val days = ChronoUnit.DAYS.between(epochToDate(epochMs), today)
    return when {
        days <= 0L -> "today"
        days == 1L -> "yesterday"
        days < 7 -> "$days days ago"
        else -> epochToDate(epochMs).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

fun shortDateTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
