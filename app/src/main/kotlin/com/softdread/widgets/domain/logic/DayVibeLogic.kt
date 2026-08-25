package com.softdread.widgets.domain.logic

import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.min

/**
 * One calendar entry, normalised away from the Android provider so the vibe
 * analysis is testable without a device.
 *
 * [isMeeting] follows the Bible's definition: a timed event with attendees or
 * video/meeting metadata. Personal reminders and tasks are not meetings.
 */
data class CalendarEntry(
    val id: Long,
    val title: String,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val isAllDay: Boolean,
    val isMeeting: Boolean,
) {
    val durationMinutes: Long
        get() = if (isAllDay) 0L else Duration.between(start, end).toMinutes().coerceAtLeast(0)
}

/** The analysed shape of a day. */
data class DayVibeReading(
    val busyScore: Int,
    val meetingCount: Int,
    val eventCount: Int,
    val bookedMinutes: Long,
    val backToBackCount: Int,
    val firstEvent: CalendarEntry?,
    val nextEvent: CalendarEntry?,
    val lastEventEnd: ZonedDateTime?,
    val freeMinutesInWorkday: Long,
    val hasEarlyStart: Boolean,
    val hasLateEvent: Boolean,
    val hasLunchGap: Boolean,
    val hasOnlyTasks: Boolean,
)

/**
 * Turns a day's calendar entries into the Content Bible's busy score and state.
 *
 * The score formula, the two penalties, the five bands and the five special
 * overlays are transcribed directly from the Bible's Day Vibe rules table.
 *
 * One term the Bible names but does not define numerically is "back-to-back".
 * This implementation counts an adjacency when the next event starts no more
 * than five minutes after the previous one ends (overlaps included), which is
 * the tightest reading that still tolerates calendars that pad meetings.
 */
object DayVibeLogic {

    private const val BACK_TO_BACK_GAP_MINUTES = 5L
    private val EARLY_THRESHOLD: LocalTime = LocalTime.of(8, 0)
    private val LATE_THRESHOLD: LocalTime = LocalTime.of(20, 0)
    private val LUNCH_WINDOW_START: LocalTime = LocalTime.of(11, 30)
    private val LUNCH_WINDOW_END: LocalTime = LocalTime.of(14, 30)
    private const val LUNCH_GAP_MINUTES = 30L
    private val WORKDAY_START: LocalTime = LocalTime.of(9, 0)
    private val WORKDAY_END: LocalTime = LocalTime.of(18, 0)

    fun analyse(entries: List<CalendarEntry>, now: ZonedDateTime): DayVibeReading {
        val timed = entries.filterNot { it.isAllDay }.sortedBy { it.start }
        val meetings = timed.filter { it.isMeeting }
        val bookedMinutes = timed.sumOf { it.durationMinutes }
        val backToBack = countBackToBack(timed)

        val firstTimed = timed.firstOrNull()
        val hasEarlyStart = firstTimed?.start?.toLocalTime()?.isBefore(EARLY_THRESHOLD) == true
        val hasLateEvent = timed.any { it.end.toLocalTime().isAfter(LATE_THRESHOLD) }

        val rawScore = bookedMinutes / 6.0 +
            meetings.size * 5.0 +
            backToBack * 8.0 +
            (if (hasEarlyStart) 8.0 else 0.0) +
            (if (hasLateEvent) 8.0 else 0.0)

        return DayVibeReading(
            busyScore = min(100.0, rawScore).toInt(),
            meetingCount = meetings.size,
            eventCount = entries.size,
            bookedMinutes = bookedMinutes,
            backToBackCount = backToBack,
            firstEvent = firstTimed,
            nextEvent = timed.firstOrNull { it.end.isAfter(now) },
            lastEventEnd = timed.maxByOrNull { it.end }?.end,
            freeMinutesInWorkday = freeMinutesInWorkday(timed, now),
            hasEarlyStart = hasEarlyStart,
            hasLateEvent = hasLateEvent,
            hasLunchGap = hasLunchGap(timed, now),
            hasOnlyTasks = meetings.isEmpty() && entries.isNotEmpty(),
        )
    }

    /** Bands: 0-19 empty, 20-39 light, 40-59 normal, 60-79 busy, 80-100 brutal. */
    fun stateKey(reading: DayVibeReading): String = when {
        reading.busyScore < 20 -> "DAY_B1"
        reading.busyScore < 40 -> "DAY_B2"
        reading.busyScore < 60 -> "DAY_B3"
        reading.busyScore < 80 -> "DAY_B4"
        else -> "DAY_B5"
    }

    /**
     * The single special overlay to apply, or `null`. The Bible allows at most
     * one; they are ordered here so the most disruptive fact about the day wins.
     */
    fun specialStateKey(reading: DayVibeReading): String? = when {
        reading.backToBackCount >= 3 -> "DAY_S2"
        !reading.hasLunchGap && reading.meetingCount > 0 -> "DAY_S3"
        reading.hasEarlyStart -> "DAY_S1"
        reading.hasLateEvent -> "DAY_S4"
        reading.hasOnlyTasks -> "DAY_S5"
        else -> null
    }

    private fun countBackToBack(timed: List<CalendarEntry>): Int {
        if (timed.size < 2) return 0
        var count = 0
        for (index in 1 until timed.size) {
            val gap = Duration.between(timed[index - 1].end, timed[index].start).toMinutes()
            if (gap <= BACK_TO_BACK_GAP_MINUTES) count++
        }
        return count
    }

    /** True when at least 30 free minutes exist between 11:30 and 14:30. */
    private fun hasLunchGap(timed: List<CalendarEntry>, now: ZonedDateTime): Boolean {
        val windowStart = now.with(LUNCH_WINDOW_START)
        val windowEnd = now.with(LUNCH_WINDOW_END)
        val busy = timed
            .map { maxOf(it.start, windowStart) to minOf(it.end, windowEnd) }
            .filter { (start, end) -> end.isAfter(start) }
            .sortedBy { it.first }

        var cursor = windowStart
        for ((start, end) in busy) {
            if (Duration.between(cursor, start).toMinutes() >= LUNCH_GAP_MINUTES) return true
            if (end.isAfter(cursor)) cursor = end
        }
        return Duration.between(cursor, windowEnd).toMinutes() >= LUNCH_GAP_MINUTES
    }

    private fun freeMinutesInWorkday(timed: List<CalendarEntry>, now: ZonedDateTime): Long {
        val start = now.with(WORKDAY_START)
        val end = now.with(WORKDAY_END)
        val total = Duration.between(start, end).toMinutes().coerceAtLeast(0)
        val busy = timed.sumOf { entry ->
            val overlapStart = maxOf(entry.start, start)
            val overlapEnd = minOf(entry.end, end)
            Duration.between(overlapStart, overlapEnd).toMinutes().coerceAtLeast(0)
        }
        return (total - busy).coerceAtLeast(0)
    }

    fun variables(reading: DayVibeReading, timeFormatter: (ZonedDateTime) -> String): Map<String, String> {
        val variables = mutableMapOf(
            "meeting_count" to reading.meetingCount.toString(),
            "booked_hours" to Formatting.oneDecimal(reading.bookedMinutes / 60.0),
            "free_hours" to Formatting.oneDecimal(reading.freeMinutesInWorkday / 60.0),
        )
        reading.firstEvent?.let { variables["first_time"] = timeFormatter(it.start) }
        val late = reading.lastEventEnd?.takeIf { it.toLocalTime().isAfter(LATE_THRESHOLD) }
        late?.let { variables["late_time"] = timeFormatter(it) }
        return variables
    }
}
