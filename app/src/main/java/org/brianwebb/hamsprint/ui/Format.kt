package org.brianwebb.hamsprint.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun formatDuration(duration: Duration): String {
    val totalMinutes = duration.inWholeMinutes
    return when {
        totalMinutes < 1 -> if (duration.isPositive()) "<1m" else "0m"
        totalMinutes < 60 -> "${totalMinutes}m"
        else -> {
            val h = totalMinutes / 60
            val m = totalMinutes % 60
            if (m == 0L) "${h}h" else "${h}h ${m}m"
        }
    }
}

fun formatMillis(millis: Long): String = formatDuration(millis.milliseconds)

fun formatPercent(p: Double): String = "${(p * 100).roundToInt()}%"

fun formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
