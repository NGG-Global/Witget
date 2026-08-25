package com.softdread.widgets.data.device

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.softdread.widgets.domain.logic.CalendarEntry
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** A calendar the user can include in or exclude from Day Vibe. */
data class CalendarAccount(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val colour: Int?,
)

/** Today's calendar, or the reason it is unavailable. */
sealed interface CalendarResult {
    data class Available(val entries: List<CalendarEntry>) : CalendarResult
    data object PermissionRequired : CalendarResult
    data class Unavailable(val reason: String) : CalendarResult
}

/**
 * Reads today's events through the Calendar Provider.
 *
 * Only `READ_CALENDAR` is declared — the pack never writes — and every query is
 * scoped to the current local day. Event titles are read so the app can offer an
 * opt-in "show titles" setting, but the Bible's privacy rule keeps them out of
 * commentary unless the user explicitly enables them for that widget.
 */
class CalendarDataSource(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun calendars(): List<CalendarAccount> {
        if (!hasPermission()) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            CalendarAccount(
                                id = cursor.getLong(0),
                                displayName = cursor.getString(1).orEmpty(),
                                accountName = cursor.getString(2).orEmpty(),
                                colour = if (cursor.isNull(3)) null else cursor.getInt(3),
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    fun readToday(
        includedCalendarIds: Set<Long> = emptySet(),
        zone: ZoneId = ZoneId.systemDefault(),
        now: ZonedDateTime = ZonedDateTime.now(zone),
    ): CalendarResult {
        if (!hasPermission()) return CalendarResult.PermissionRequired

        val dayStart = now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        // CalendarContract.Instances expands recurring events, so a weekly
        // stand-up is returned as today's occurrence rather than as its series.
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, dayStart)
            ContentUris.appendId(this, dayEnd)
        }.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.HAS_ATTENDEE_DATA,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.AVAILABILITY,
        )

        return runCatching {
            val entries = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor -> readEntries(cursor, includedCalendarIds, zone) }.orEmpty()
            CalendarResult.Available(entries)
        }.getOrElse { CalendarResult.Unavailable(it.message ?: "calendar query failed") }
    }

    private fun readEntries(
        cursor: Cursor,
        includedCalendarIds: Set<Long>,
        zone: ZoneId,
    ): List<CalendarEntry> = buildList {
        while (cursor.moveToNext()) {
            val calendarId = cursor.getLong(5)
            if (includedCalendarIds.isNotEmpty() && calendarId !in includedCalendarIds) continue

            val status = if (cursor.isNull(8)) null else cursor.getInt(8)
            if (status == CalendarContract.Instances.STATUS_CANCELED) continue

            val selfStatus = if (cursor.isNull(9)) null else cursor.getInt(9)
            if (selfStatus == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue

            val isAllDay = cursor.getInt(4) == 1
            val begin = cursor.getLong(2)
            val end = cursor.getLong(3)
            if (end <= begin && !isAllDay) continue

            add(
                CalendarEntry(
                    id = cursor.getLong(0),
                    title = cursor.getString(1).orEmpty(),
                    start = ZonedDateTime.ofInstant(Instant.ofEpochMilli(begin), zone),
                    end = ZonedDateTime.ofInstant(Instant.ofEpochMilli(end), zone),
                    isAllDay = isAllDay,
                    isMeeting = isMeeting(cursor),
                ),
            )
        }
    }

    /**
     * The Bible's meeting definition: a timed event with attendees or
     * video/meeting metadata. A location that names a conferencing service
     * counts, which is how a solo-organiser video call still reads as a meeting.
     */
    private fun isMeeting(cursor: Cursor): Boolean {
        if (cursor.getInt(4) == 1) return false
        val hasAttendees = !cursor.isNull(6) && cursor.getInt(6) == 1
        if (hasAttendees) return true
        val location = cursor.getString(7).orEmpty().lowercase()
        if (CONFERENCING_HINTS.any { it in location }) return true
        // An event marked BUSY that blocks real time is treated as a meeting
        // only when it also carries attendee data, so personal focus blocks and
        // reminders stay out of the meeting count.
        return false
    }

    private companion object {
        val CONFERENCING_HINTS = listOf(
            "meet.google", "zoom.us", "teams.microsoft", "webex", "whereby.com", "bluejeans",
        )
    }
}
