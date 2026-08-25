package com.softdread.widgets.domain.logic

import com.softdread.widgets.core.time.Clock
import com.softdread.widgets.data.prefs.CountdownUnit
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.ceil

/** How far away a countdown target is, in every unit the Bible defines. */
data class CountdownReading(
    val title: String,
    val target: ZonedDateTime,
    val duration: Duration,
    val hasPassed: Boolean,
    val totalMinutes: Long,
    val totalHours: Long,
    val totalDays: Long,
    val sleeps: Long,
    val weeks: Double,
    val weekends: Double,
    val months: Double,
    val years: Double,
)

/**
 * Countdown distance bands and the human-scale units the Bible frames them in.
 *
 * Calculations run on absolute instants and are rendered in the countdown's own
 * zone, so a target set while travelling still lands on the intended local
 * moment. Days are counted as whole calendar days between the two local dates
 * rather than as 24-hour blocks, which is what makes "tomorrow" read as 1 day
 * even when the target is 30 hours away.
 */
object CountdownLogic {

    private const val DAYS_PER_MONTH = 30.44
    private const val DAYS_PER_YEAR = 365.2425

    fun read(
        title: String,
        targetEpochMillis: Long,
        clock: Clock,
        zoneId: ZoneId? = null,
    ): CountdownReading {
        val zone = zoneId ?: clock.zone()
        val now = ZonedDateTime.ofInstant(clock.now(), zone)
        val target = ZonedDateTime.ofInstant(Instant.ofEpochMilli(targetEpochMillis), zone)
        val duration = Duration.between(now, target)
        val hasPassed = duration.isNegative
        val absolute = duration.abs()

        val calendarDays = java.time.temporal.ChronoUnit.DAYS.between(
            now.toLocalDate(),
            target.toLocalDate(),
        )
        val totalDays = abs(calendarDays)
        val totalHours = absolute.toHours()
        val totalMinutes = absolute.toMinutes()
        val sleeps = if (hasPassed) 0L else maxOf(1L, ceil(absolute.toMinutes() / (24.0 * 60.0)).toLong())
        val exactDays = absolute.toMinutes() / (24.0 * 60.0)

        return CountdownReading(
            title = title,
            target = target,
            duration = duration,
            hasPassed = hasPassed,
            totalMinutes = totalMinutes,
            totalHours = totalHours,
            totalDays = totalDays,
            sleeps = sleeps,
            weeks = exactDays / 7.0,
            weekends = exactDays / 7.0,
            months = exactDays / DAYS_PER_MONTH,
            years = exactDays / DAYS_PER_YEAR,
        )
    }

    /** Bands: <1 h, 1-23 h, 1-2 d, 3-6 d, 1-3 w, 1-3 mo, 3-12 mo, >=1 y. */
    fun stateKey(reading: CountdownReading): String {
        val hours = reading.totalHours
        val days = reading.totalDays
        return when {
            hours < 1 -> "CD1"
            days < 1 -> "CD2"
            days <= 2 -> "CD3"
            days <= 6 -> "CD4"
            days <= 21 -> "CD5"
            days <= 92 -> "CD6"
            days < 365 -> "CD7"
            else -> "CD8"
        }
    }

    /**
     * The unit to lead with when the instance is set to `AUTO`, chosen so the
     * displayed number stays in a readable range rather than reading
     * "0.1 months".
     */
    fun automaticUnit(reading: CountdownReading): CountdownUnit = when {
        reading.totalDays <= 2 -> CountdownUnit.SLEEPS
        reading.totalDays <= 21 -> CountdownUnit.WEEKENDS
        reading.totalDays <= 92 -> CountdownUnit.WEEKS
        else -> CountdownUnit.MONTHS
    }

    /** "2.7 weekends", "19 sleeps" — the secondary line the design sheet requires. */
    fun perspectiveLine(reading: CountdownReading, unit: CountdownUnit): String {
        val resolved = if (unit == CountdownUnit.AUTO) automaticUnit(reading) else unit
        return when (resolved) {
            CountdownUnit.SLEEPS -> "${Formatting.integer(reading.sleeps)} sleeps"
            CountdownUnit.WEEKENDS -> "about ${Formatting.oneDecimal(reading.weekends)} weekends"
            CountdownUnit.WEEKS -> "${Formatting.oneDecimal(reading.weeks)} weeks"
            CountdownUnit.MONTHS -> "${Formatting.oneDecimal(reading.months)} months"
            CountdownUnit.AUTO -> "${Formatting.oneDecimal(reading.weekends)} weekends"
        }
    }

    fun variables(reading: CountdownReading): Map<String, String> = mapOf(
        "event" to reading.title,
        "event_upper" to reading.title.uppercase(java.util.Locale.getDefault()),
        "minutes" to Formatting.integer(reading.totalMinutes),
        "hours" to Formatting.integer(reading.totalHours),
        "days" to Formatting.integer(reading.totalDays),
        "sleeps" to Formatting.integer(reading.sleeps),
        "weeks" to Formatting.oneDecimal(reading.weeks),
        "weekends" to Formatting.oneDecimal(reading.weekends),
        "months" to Formatting.oneDecimal(reading.months),
        "years" to Formatting.oneDecimal(reading.years),
    )
}
