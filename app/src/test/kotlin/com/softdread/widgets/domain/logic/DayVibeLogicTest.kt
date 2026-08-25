package com.softdread.widgets.domain.logic

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

/**
 * Day Vibe aggregation, tested entirely without the Android calendar provider.
 *
 * The score formula from the Bible is
 * `min(100, booked/6 + meetings*5 + backToBack*8 + early + late)`, with +8
 * penalties for a start before 08:00 and an end after 20:00.
 */
class DayVibeLogicTest {

    private val zone = ZoneId.of("Europe/London")
    private val day = LocalDate.of(2026, 6, 17)
    private val now = ZonedDateTime.of(day, LocalTime.of(9, 0), zone)

    private var nextId = 1L

    private fun event(
        from: String,
        to: String,
        meeting: Boolean = true,
        allDay: Boolean = false,
    ) = CalendarEntry(
        id = nextId++,
        title = "Event",
        start = ZonedDateTime.of(day, LocalTime.parse(from), zone),
        end = ZonedDateTime.of(day, LocalTime.parse(to), zone),
        isAllDay = allDay,
        isMeeting = meeting,
    )

    @Test
    fun `an empty calendar is the empty band`() {
        val reading = DayVibeLogic.analyse(emptyList(), now)
        assertThat(reading.busyScore).isEqualTo(0)
        assertThat(reading.meetingCount).isEqualTo(0)
        assertThat(DayVibeLogic.stateKey(reading)).isEqualTo("DAY_B1")
    }

    @Test
    fun `booked minutes and meeting count drive the score`() {
        // Two one-hour meetings, well spaced: 120/6 + 2*5 = 30.
        val reading = DayVibeLogic.analyse(
            listOf(event("10:00", "11:00"), event("14:00", "15:00")),
            now,
        )
        assertThat(reading.bookedMinutes).isEqualTo(120)
        assertThat(reading.meetingCount).isEqualTo(2)
        assertThat(reading.backToBackCount).isEqualTo(0)
        assertThat(reading.busyScore).isEqualTo(30)
        assertThat(DayVibeLogic.stateKey(reading)).isEqualTo("DAY_B2")
    }

    @Test
    fun `back to back events add eight points each`() {
        val spaced = DayVibeLogic.analyse(
            listOf(event("10:00", "11:00"), event("13:00", "14:00")),
            now,
        )
        val adjacent = DayVibeLogic.analyse(
            listOf(event("10:00", "11:00"), event("11:00", "12:00")),
            now,
        )
        assertThat(adjacent.backToBackCount).isEqualTo(1)
        assertThat(adjacent.busyScore - spaced.busyScore).isEqualTo(8)
    }

    @Test
    fun `an early start adds the early penalty and sets the overlay`() {
        val reading = DayVibeLogic.analyse(listOf(event("07:30", "08:30")), now)
        assertThat(reading.hasEarlyStart).isTrue()
        // 60/6 + 1*5 + 8 = 23.
        assertThat(reading.busyScore).isEqualTo(23)
        assertThat(DayVibeLogic.specialStateKey(reading)).isEqualTo("DAY_S1")
    }

    @Test
    fun `eight o'clock exactly is not early`() {
        val reading = DayVibeLogic.analyse(listOf(event("08:00", "09:00")), now)
        assertThat(reading.hasEarlyStart).isFalse()
    }

    @Test
    fun `a late finish adds the late penalty`() {
        val reading = DayVibeLogic.analyse(listOf(event("19:30", "20:30")), now)
        assertThat(reading.hasLateEvent).isTrue()
        assertThat(reading.busyScore).isEqualTo(23)
    }

    @Test
    fun `the score is capped at one hundred`() {
        val packed = (8..19).map { hour ->
            event("%02d:00".format(hour), "%02d:00".format(hour + 1))
        }
        val reading = DayVibeLogic.analyse(packed, now)
        assertThat(reading.busyScore).isEqualTo(100)
        assertThat(DayVibeLogic.stateKey(reading)).isEqualTo("DAY_B5")
    }

    @Test
    fun `bands sit on the Bible boundaries`() {
        fun bandForScore(entries: List<CalendarEntry>) =
            DayVibeLogic.stateKey(DayVibeLogic.analyse(entries, now))

        assertThat(bandForScore(emptyList())).isEqualTo("DAY_B1")
        // 120 booked + 2 meetings = 30 -> light.
        assertThat(bandForScore(listOf(event("10:00", "11:00"), event("14:00", "15:00"))))
            .isEqualTo("DAY_B2")
        // 240 booked + 4 meetings = 60 -> busy.
        assertThat(
            bandForScore(
                listOf(
                    event("09:00", "10:00"), event("11:00", "12:00"),
                    event("13:00", "14:00"), event("15:00", "16:00"),
                ),
            ),
        ).isEqualTo("DAY_B4")
    }

    @Test
    fun `tasks and reminders are not meetings`() {
        val reading = DayVibeLogic.analyse(
            listOf(event("10:00", "11:00", meeting = false), event("12:00", "12:30", meeting = false)),
            now,
        )
        assertThat(reading.meetingCount).isEqualTo(0)
        assertThat(reading.hasOnlyTasks).isTrue()
        assertThat(DayVibeLogic.specialStateKey(reading)).isEqualTo("DAY_S5")
    }

    @Test
    fun `all day events do not consume booked time`() {
        val reading = DayVibeLogic.analyse(
            listOf(event("00:00", "23:59", meeting = false, allDay = true)),
            now,
        )
        assertThat(reading.bookedMinutes).isEqualTo(0)
        assertThat(reading.busyScore).isEqualTo(0)
    }

    @Test
    fun `a blocked lunch window raises the lunch overlay`() {
        val blocked = DayVibeLogic.analyse(listOf(event("11:00", "15:00")), now)
        assertThat(blocked.hasLunchGap).isFalse()
        assertThat(DayVibeLogic.specialStateKey(blocked)).isEqualTo("DAY_S3")

        val free = DayVibeLogic.analyse(listOf(event("11:00", "12:00")), now)
        assertThat(free.hasLunchGap).isTrue()
    }

    @Test
    fun `three back to back events outrank every other overlay`() {
        val reading = DayVibeLogic.analyse(
            listOf(
                event("09:00", "10:00"),
                event("10:00", "11:00"),
                event("11:00", "12:00"),
                event("12:00", "13:00"),
            ),
            now,
        )
        assertThat(reading.backToBackCount).isAtLeast(3)
        assertThat(DayVibeLogic.specialStateKey(reading)).isEqualTo("DAY_S2")
    }

    @Test
    fun `variables carry the useful metrics`() {
        val reading = DayVibeLogic.analyse(
            listOf(event("09:30", "10:30"), event("14:00", "15:30")),
            now,
        )
        val variables = DayVibeLogic.variables(reading) { it.toLocalTime().toString() }
        assertThat(variables["meeting_count"]).isEqualTo("2")
        assertThat(variables["booked_hours"]).isEqualTo("2.5")
        assertThat(variables["first_time"]).isEqualTo("09:30")
    }
}
