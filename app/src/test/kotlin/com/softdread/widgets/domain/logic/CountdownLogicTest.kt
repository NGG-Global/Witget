package com.softdread.widgets.domain.logic

import com.google.common.truth.Truth.assertThat
import com.softdread.widgets.core.time.FixedClock
import com.softdread.widgets.data.prefs.CountdownUnit
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

/**
 * Countdown arithmetic across the boundaries that usually break it: today,
 * tomorrow, elapsed targets, month ends, year ends, a leap day, and two devices
 * in different time zones looking at the same instant.
 */
class CountdownLogicTest {

    private val london = ZoneId.of("Europe/London")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun clockAt(local: String, zone: ZoneId = london) =
        FixedClock(LocalDateTime.parse(local).atZone(zone).toInstant(), zone)

    private fun targetAt(local: String, zone: ZoneId = london) =
        LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `later today reads as hours, not a day`() {
        val reading = CountdownLogic.read(
            title = "Dentist",
            targetEpochMillis = targetAt("2026-03-10T17:00"),
            clock = clockAt("2026-03-10T09:00"),
        )
        assertThat(reading.hasPassed).isFalse()
        assertThat(reading.totalDays).isEqualTo(0)
        assertThat(reading.totalHours).isEqualTo(8)
        assertThat(CountdownLogic.stateKey(reading)).isEqualTo("CD2")
    }

    @Test
    fun `under an hour is its own band`() {
        val reading = CountdownLogic.read(
            title = "Train",
            targetEpochMillis = targetAt("2026-03-10T09:40"),
            clock = clockAt("2026-03-10T09:00"),
        )
        assertThat(reading.totalMinutes).isEqualTo(40)
        assertThat(CountdownLogic.stateKey(reading)).isEqualTo("CD1")
    }

    @Test
    fun `tomorrow morning is one day even when fewer than 24 hours away`() {
        // 23:00 tonight to 08:00 tomorrow is 9 hours but one calendar day, which
        // is how a person counts it.
        val reading = CountdownLogic.read(
            title = "Flight",
            targetEpochMillis = targetAt("2026-03-11T08:00"),
            clock = clockAt("2026-03-10T23:00"),
        )
        assertThat(reading.totalDays).isEqualTo(1)
        assertThat(reading.sleeps).isEqualTo(1)
        assertThat(CountdownLogic.stateKey(reading)).isEqualTo("CD3")
    }

    @Test
    fun `an elapsed target is reported as passed rather than as a negative countdown`() {
        val reading = CountdownLogic.read(
            title = "Deadline",
            targetEpochMillis = targetAt("2026-03-01T12:00"),
            clock = clockAt("2026-03-10T12:00"),
        )
        assertThat(reading.hasPassed).isTrue()
        assertThat(reading.totalDays).isEqualTo(9)
    }

    @Test
    fun `month boundaries are counted in calendar days`() {
        val reading = CountdownLogic.read(
            title = "Rent",
            targetEpochMillis = targetAt("2026-04-01T00:00"),
            clock = clockAt("2026-03-28T00:00"),
        )
        assertThat(reading.totalDays).isEqualTo(4)
    }

    @Test
    fun `year boundaries are counted in calendar days`() {
        val reading = CountdownLogic.read(
            title = "New year",
            targetEpochMillis = targetAt("2027-01-01T00:00"),
            clock = clockAt("2026-12-30T00:00"),
        )
        assertThat(reading.totalDays).isEqualTo(2)
        assertThat(CountdownLogic.stateKey(reading)).isEqualTo("CD3")
    }

    @Test
    fun `a leap day is a real day in the count`() {
        val leap = CountdownLogic.read(
            title = "Leap",
            targetEpochMillis = targetAt("2028-03-01T00:00"),
            clock = clockAt("2028-02-28T00:00"),
        )
        val nonLeap = CountdownLogic.read(
            title = "Leap",
            targetEpochMillis = targetAt("2027-03-01T00:00"),
            clock = clockAt("2027-02-28T00:00"),
        )
        assertThat(leap.totalDays).isEqualTo(2)
        assertThat(nonLeap.totalDays).isEqualTo(1)
    }

    @Test
    fun `distance bands cover the whole Bible range`() {
        fun bandFor(days: Long): String {
            val reading = CountdownLogic.read(
                title = "T",
                targetEpochMillis = ZonedDateTime.parse("2026-03-10T09:00Z")
                    .plusDays(days).toInstant().toEpochMilli(),
                clock = FixedClock(ZonedDateTime.parse("2026-03-10T09:00Z").toInstant(), ZoneId.of("UTC")),
            )
            return CountdownLogic.stateKey(reading)
        }
        assertThat(bandFor(2)).isEqualTo("CD3")
        assertThat(bandFor(3)).isEqualTo("CD4")
        assertThat(bandFor(6)).isEqualTo("CD4")
        assertThat(bandFor(7)).isEqualTo("CD5")
        assertThat(bandFor(21)).isEqualTo("CD5")
        assertThat(bandFor(22)).isEqualTo("CD6")
        assertThat(bandFor(92)).isEqualTo("CD6")
        assertThat(bandFor(93)).isEqualTo("CD7")
        assertThat(bandFor(364)).isEqualTo("CD7")
        assertThat(bandFor(365)).isEqualTo("CD8")
    }

    @Test
    fun `the countdown is rendered in its own zone, so travelling does not move it`() {
        val target = targetAt("2026-03-20T09:00", tokyo)
        val fromTokyo = CountdownLogic.read("Trip", target, clockAt("2026-03-19T09:00", tokyo), tokyo)
        val fromLondon = CountdownLogic.read("Trip", target, clockAt("2026-03-19T00:00", london), tokyo)
        // Both observers are looking at the same instant, so the remaining
        // duration matches to the minute regardless of where they are.
        assertThat(fromTokyo.totalMinutes).isEqualTo(fromLondon.totalMinutes)
    }

    @Test
    fun `human scale units follow the Bible conversions`() {
        val reading = CountdownLogic.read(
            title = "Vacation",
            targetEpochMillis = targetAt("2026-03-29T09:00"),
            clock = clockAt("2026-03-10T09:00"),
        )
        assertThat(reading.totalDays).isEqualTo(19)
        assertThat(reading.sleeps).isEqualTo(19)
        assertThat(reading.weekends).isWithin(0.01).of(19.0 / 7.0)
        assertThat(reading.months).isWithin(0.01).of(19.0 / 30.44)
        assertThat(reading.years).isWithin(0.001).of(19.0 / 365.2425)
        assertThat(CountdownLogic.perspectiveLine(reading, CountdownUnit.WEEKENDS))
            .isEqualTo("about 2.7 weekends")
    }

    @Test
    fun `sleeps is never zero for a future event`() {
        val reading = CountdownLogic.read(
            title = "Soon",
            targetEpochMillis = targetAt("2026-03-10T09:30"),
            clock = clockAt("2026-03-10T09:00"),
        )
        assertThat(reading.sleeps).isEqualTo(1)
    }

    @Test
    fun `automatic unit keeps the displayed number readable`() {
        fun unitFor(days: Long): CountdownUnit {
            val reading = CountdownLogic.read(
                title = "T",
                targetEpochMillis = ZonedDateTime.parse("2026-03-10T09:00Z")
                    .plusDays(days).toInstant().toEpochMilli(),
                clock = FixedClock(ZonedDateTime.parse("2026-03-10T09:00Z").toInstant(), ZoneId.of("UTC")),
            )
            return CountdownLogic.automaticUnit(reading)
        }
        assertThat(unitFor(1)).isEqualTo(CountdownUnit.SLEEPS)
        assertThat(unitFor(14)).isEqualTo(CountdownUnit.WEEKENDS)
        assertThat(unitFor(60)).isEqualTo(CountdownUnit.WEEKS)
        assertThat(unitFor(200)).isEqualTo(CountdownUnit.MONTHS)
    }

    @Test
    fun `every Bible variable resolves to a non-blank value`() {
        val reading = CountdownLogic.read(
            title = "Vacation",
            targetEpochMillis = targetAt("2026-03-29T09:00"),
            clock = clockAt("2026-03-10T09:00"),
        )
        val variables = CountdownLogic.variables(reading)
        listOf("event", "event_upper", "minutes", "hours", "days", "sleeps", "weeks", "weekends", "months", "years")
            .forEach { key -> assertThat(variables[key]).isNotEmpty() }
        assertThat(variables["event_upper"]).isEqualTo("VACATION")
    }
}
