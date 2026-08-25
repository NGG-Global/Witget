package com.softdread.widgets.domain.logic

import com.softdread.widgets.core.time.Clock
import com.softdread.widgets.data.prefs.ProgressScope
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Progress through one period, with the boundaries it was computed from. */
data class ProgressReading(
    val scope: ProgressScope,
    val percent: Double,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    /** Stable identity of this period, used for the adjacent-period repeat rule. */
    val periodKey: String,
    /** Human label: "the year", "august", "week 35", "today". */
    val label: String,
    /** The scope's headline value: "2026", "august", "week 35", "wednesday". */
    val headline: String,
)

/**
 * Progress through the day, week, month or year.
 *
 * All arithmetic runs through `java.time` in the user's zone, so varying month
 * lengths, leap years, week boundaries and DST-shortened days are handled by the
 * platform rather than by hardcoded constants. The Bible clamps to 0-99.9 before
 * rollover so a widget never sits at "100% complete".
 */
object TimeProgressLogic {

    private const val MAX_PERCENT = 99.9

    fun read(
        scope: ProgressScope,
        clock: Clock,
        weekStartsOnMonday: Boolean = true,
        locale: Locale = Locale.getDefault(),
    ): ProgressReading {
        val zone = clock.zone()
        val now = ZonedDateTime.ofInstant(clock.now(), zone)
        val (start, end) = boundaries(scope, now, weekStartsOnMonday)
        val elapsed = java.time.Duration.between(start, now).toMillis().toDouble()
        val total = java.time.Duration.between(start, end).toMillis().toDouble()
        val percent = if (total <= 0.0) 0.0 else (elapsed / total * 100.0).coerceIn(0.0, MAX_PERCENT)
        return ProgressReading(
            scope = scope,
            percent = percent,
            start = start,
            end = end,
            periodKey = periodKey(scope, start, weekStartsOnMonday),
            label = label(scope, start, locale),
            headline = headline(scope, start, locale),
        )
    }

    /**
     * Half-open period boundaries `[start, end)`. The end of a period is the
     * start of the next one, which is what makes rollover exact — at the
     * boundary the reading flips to 0% of the new period instead of lingering
     * at 100% of the old one.
     */
    fun boundaries(
        scope: ProgressScope,
        now: ZonedDateTime,
        weekStartsOnMonday: Boolean,
    ): Pair<ZonedDateTime, ZonedDateTime> {
        val zone = now.zone
        val date = now.toLocalDate()
        return when (scope) {
            ProgressScope.DAY -> {
                val start = date.atStartOfDay(zone)
                start to date.plusDays(1).atStartOfDay(zone)
            }
            ProgressScope.WEEK -> {
                val firstDay = if (weekStartsOnMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
                val startDate = date.with(TemporalAdjusters.previousOrSame(firstDay))
                startDate.atStartOfDay(zone) to startDate.plusWeeks(1).atStartOfDay(zone)
            }
            ProgressScope.MONTH -> {
                val startDate = date.withDayOfMonth(1)
                startDate.atStartOfDay(zone) to startDate.plusMonths(1).atStartOfDay(zone)
            }
            ProgressScope.YEAR -> {
                val startDate = LocalDate.of(date.year, 1, 1)
                startDate.atStartOfDay(zone) to startDate.plusYears(1).atStartOfDay(zone)
            }
        }
    }

    /** Bands: 0-14%, 15-34%, 35-64%, 65-84%, 85-99%. */
    fun stateKey(percent: Double): String = when {
        percent < 15.0 -> "TP1"
        percent < 35.0 -> "TP2"
        percent < 65.0 -> "TP3"
        percent < 85.0 -> "TP4"
        else -> "TP5"
    }

    /**
     * The period immediately before this one, so a response shown last week is
     * excluded this week per the Bible's Time Progress rule.
     */
    fun previousPeriodKey(reading: ProgressReading, weekStartsOnMonday: Boolean): String {
        val previousStart = when (reading.scope) {
            ProgressScope.DAY -> reading.start.minusDays(1)
            ProgressScope.WEEK -> reading.start.minusWeeks(1)
            ProgressScope.MONTH -> reading.start.minusMonths(1)
            ProgressScope.YEAR -> reading.start.minusYears(1)
        }
        return periodKey(reading.scope, previousStart, weekStartsOnMonday)
    }

    private fun periodKey(scope: ProgressScope, start: ZonedDateTime, weekStartsOnMonday: Boolean): String {
        val date = start.toLocalDate()
        return when (scope) {
            ProgressScope.DAY -> "day:$date"
            ProgressScope.WEEK -> "week:${date}:${if (weekStartsOnMonday) "mon" else "sun"}"
            ProgressScope.MONTH -> "month:${date.year}-${"%02d".format(date.monthValue)}"
            ProgressScope.YEAR -> "year:${date.year}"
        }
    }

    private fun label(scope: ProgressScope, start: ZonedDateTime, locale: Locale): String = when (scope) {
        ProgressScope.DAY -> "today"
        ProgressScope.WEEK -> "week ${start.get(java.time.temporal.WeekFields.of(locale).weekOfWeekBasedYear())}"
        ProgressScope.MONTH -> start.month.getDisplayName(java.time.format.TextStyle.FULL, locale).lowercase(locale)
        ProgressScope.YEAR -> "the year"
    }

    private fun headline(scope: ProgressScope, start: ZonedDateTime, locale: Locale): String = when (scope) {
        ProgressScope.DAY -> start.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale).lowercase(locale)
        ProgressScope.WEEK -> label(scope, start, locale)
        ProgressScope.MONTH -> label(scope, start, locale)
        ProgressScope.YEAR -> start.year.toString()
    }

    fun variables(reading: ProgressReading): Map<String, String> {
        // Floor rather than round: the Bible clamps a period to 99.9% before
        // rollover, and rounding 99.9 up would put "100%" on a tile that is
        // explicitly never supposed to say that.
        val whole = reading.percent.toInt().coerceIn(0, 99).toString()
        val remaining = (100 - whole.toInt()).coerceIn(0, 100)
        val period = reading.scope.key
        return mapOf(
            "percent" to whole,
            "remaining_percent" to remaining.toString(),
            "period" to period,
            "period_upper" to period.uppercase(Locale.ROOT),
        )
    }

    /** Local midnight-anchored helper used by tests and by the day scope. */
    fun startOfDay(dateTime: LocalDateTime): LocalDateTime = dateTime.toLocalDate().atStartOfDay()
}
