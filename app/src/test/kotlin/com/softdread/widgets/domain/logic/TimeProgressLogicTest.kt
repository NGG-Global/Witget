package com.softdread.widgets.domain.logic

import com.google.common.truth.Truth.assertThat
import com.softdread.widgets.core.time.FixedClock
import com.softdread.widgets.data.prefs.ProgressScope
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

/**
 * Time Progress arithmetic: real period boundaries in the local zone, correct
 * behaviour across leap years, short months, week starts and DST.
 */
class TimeProgressLogicTest {

    private val london = ZoneId.of("Europe/London")
    private val utc = ZoneId.of("UTC")

    private fun clockAt(local: String, zone: ZoneId = london) =
        FixedClock(LocalDateTime.parse(local).atZone(zone).toInstant(), zone)

    @Test
    fun `midday is half the day`() {
        val reading = TimeProgressLogic.read(ProgressScope.DAY, clockAt("2026-06-15T12:00"))
        assertThat(reading.percent).isWithin(0.1).of(50.0)
    }

    @Test
    fun `a year is measured against its real length`() {
        // 2026 is not a leap year: 1 July 00:00 is day 181 of 365.
        val reading = TimeProgressLogic.read(ProgressScope.YEAR, clockAt("2026-07-01T00:00"))
        assertThat(reading.percent).isWithin(0.05).of(181.0 / 365.0 * 100.0)
    }

    @Test
    fun `a leap year has 366 days in the denominator`() {
        // Assert the period length directly: percentages alone would not
        // distinguish a leap year, because the extra day lands in February and
        // so appears in the numerator as well as the denominator.
        fun yearLength(local: String): Long {
            val clock = clockAt(local, utc)
            val (start, end) = TimeProgressLogic.boundaries(
                ProgressScope.YEAR,
                java.time.ZonedDateTime.ofInstant(clock.now(), clock.zone()),
                weekStartsOnMonday = true,
            )
            return java.time.Duration.between(start, end).toDays()
        }
        assertThat(yearLength("2028-07-01T00:00")).isEqualTo(366)
        assertThat(yearLength("2026-07-01T00:00")).isEqualTo(365)

        val leap = TimeProgressLogic.read(ProgressScope.YEAR, clockAt("2028-07-01T00:00", utc))
        assertThat(leap.percent).isWithin(0.01).of(182.0 / 366.0 * 100.0)
    }

    @Test
    fun `month length is never assumed`() {
        // Measured in UTC so this is pure calendar arithmetic; the extra hour a
        // real zone's DST transition adds has its own test below.
        val february = TimeProgressLogic.read(ProgressScope.MONTH, clockAt("2026-02-15T00:00", utc))
        val march = TimeProgressLogic.read(ProgressScope.MONTH, clockAt("2026-03-15T00:00", utc))
        val april = TimeProgressLogic.read(ProgressScope.MONTH, clockAt("2026-04-15T00:00", utc))
        assertThat(february.percent).isWithin(0.01).of(14.0 / 28.0 * 100.0)
        assertThat(march.percent).isWithin(0.01).of(14.0 / 31.0 * 100.0)
        assertThat(april.percent).isWithin(0.01).of(14.0 / 30.0 * 100.0)
    }

    @Test
    fun `a leap February is 29 days long`() {
        val leapFebruary = TimeProgressLogic.read(ProgressScope.MONTH, clockAt("2028-02-15T00:00", utc))
        assertThat(leapFebruary.percent).isWithin(0.01).of(14.0 / 29.0 * 100.0)
    }

    @Test
    fun `week start setting moves the boundary`() {
        // 2026-06-15 is a Monday.
        val monday = TimeProgressLogic.read(ProgressScope.WEEK, clockAt("2026-06-15T00:00"), weekStartsOnMonday = true)
        val sunday = TimeProgressLogic.read(ProgressScope.WEEK, clockAt("2026-06-15T00:00"), weekStartsOnMonday = false)
        assertThat(monday.percent).isWithin(0.05).of(0.0)
        assertThat(sunday.percent).isWithin(0.05).of(1.0 / 7.0 * 100.0)
    }

    @Test
    fun `percent never reaches one hundred before rollover`() {
        val reading = TimeProgressLogic.read(
            ProgressScope.DAY,
            clockAt("2026-06-15T23:59:59.999"),
        )
        assertThat(reading.percent).isLessThan(100.0)
        assertThat(reading.percent).isAtLeast(99.0)
    }

    @Test
    fun `a new period starts at zero rather than lingering at full`() {
        val endOfYear = TimeProgressLogic.read(ProgressScope.YEAR, clockAt("2026-12-31T23:59"))
        val newYear = TimeProgressLogic.read(ProgressScope.YEAR, clockAt("2027-01-01T00:00"))
        assertThat(endOfYear.percent).isGreaterThan(99.0)
        assertThat(newYear.percent).isWithin(0.01).of(0.0)
        assertThat(newYear.periodKey).isNotEqualTo(endOfYear.periodKey)
    }

    @Test
    fun `a DST-shortened day is still a whole day of progress`() {
        // 29 March 2026 is the UK spring-forward date: the day is 23 hours long.
        val boundaries = TimeProgressLogic.boundaries(
            ProgressScope.DAY,
            clockAt("2026-03-29T12:00").let {
                java.time.ZonedDateTime.ofInstant(it.now(), it.zone())
            },
            weekStartsOnMonday = true,
        )
        val hours = java.time.Duration.between(boundaries.first, boundaries.second).toHours()
        assertThat(hours).isEqualTo(23)
    }

    @Test
    fun `percent bands sit on the Bible boundaries`() {
        assertThat(TimeProgressLogic.stateKey(0.0)).isEqualTo("TP1")
        assertThat(TimeProgressLogic.stateKey(14.9)).isEqualTo("TP1")
        assertThat(TimeProgressLogic.stateKey(15.0)).isEqualTo("TP2")
        assertThat(TimeProgressLogic.stateKey(34.9)).isEqualTo("TP2")
        assertThat(TimeProgressLogic.stateKey(35.0)).isEqualTo("TP3")
        assertThat(TimeProgressLogic.stateKey(64.9)).isEqualTo("TP3")
        assertThat(TimeProgressLogic.stateKey(65.0)).isEqualTo("TP4")
        assertThat(TimeProgressLogic.stateKey(84.9)).isEqualTo("TP4")
        assertThat(TimeProgressLogic.stateKey(85.0)).isEqualTo("TP5")
        assertThat(TimeProgressLogic.stateKey(99.9)).isEqualTo("TP5")
    }

    @Test
    fun `the previous period key differs for every scope`() {
        listOf(ProgressScope.DAY, ProgressScope.WEEK, ProgressScope.MONTH, ProgressScope.YEAR).forEach { scope ->
            val reading = TimeProgressLogic.read(scope, clockAt("2026-06-15T12:00"))
            val previous = TimeProgressLogic.previousPeriodKey(reading, weekStartsOnMonday = true)
            assertThat(previous).isNotEqualTo(reading.periodKey)
        }
    }

    @Test
    fun `variables expose percent and its complement`() {
        val reading = TimeProgressLogic.read(ProgressScope.YEAR, clockAt("2026-07-01T00:00"))
        val variables = TimeProgressLogic.variables(reading)
        val percent = variables.getValue("percent").toInt()
        val remaining = variables.getValue("remaining_percent").toInt()
        assertThat(percent + remaining).isEqualTo(100)
        assertThat(variables["period"]).isEqualTo("year")
        assertThat(variables["period_upper"]).isEqualTo("YEAR")
    }
}
