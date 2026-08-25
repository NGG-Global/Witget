package com.softdread.widgets.widgets.dayvibe

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.softdread.widgets.data.content.pool
import com.softdread.widgets.data.device.CalendarDataSource
import com.softdread.widgets.data.device.CalendarResult
import com.softdread.widgets.domain.logic.DayVibeLogic
import com.softdread.widgets.domain.logic.DayVibeReading
import com.softdread.widgets.domain.logic.Formatting
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.domain.selection.AntiRepeatPolicies
import com.softdread.widgets.widgets.common.LeadingVisual
import com.softdread.widgets.widgets.common.SoftDreadWidget
import com.softdread.widgets.widgets.common.TileContent
import com.softdread.widgets.widgets.common.WidgetEnvironment
import com.softdread.widgets.widgets.common.WidgetPayload
import com.softdread.widgets.widgets.common.openAppAction
import com.softdread.widgets.widgets.common.refreshAction
import com.softdread.widgets.widgets.common.setupContent
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Day Vibe.
 *
 * The calendar is read locally, aggregated into the Content Bible's busy score
 * and then discarded — nothing about the day leaves the device, and event titles
 * never appear in commentary unless the instance explicitly opts in, per the
 * Bible's privacy rule.
 *
 * A special overlay replaces the band response when the day has a defining
 * feature: three or more back-to-back events, no lunch gap, an early start, a
 * late finish, or tasks with no meetings at all.
 */
class DayVibeWidget : SoftDreadWidget(WidgetType.DAY_VIBE) {

    override suspend fun buildPayload(environment: WidgetEnvironment): WidgetPayload {
        val action = openAppAction(environment.context, WidgetType.DAY_VIBE, environment.config.appWidgetId)
        val source = CalendarDataSource(environment.context)
        val now = ZonedDateTime.ofInstant(environment.clock.now(), environment.clock.zone())

        val entries = when (
            val result = source.readToday(
                includedCalendarIds = environment.config.calendarIds.toSet(),
                zone = environment.clock.zone(),
                now = now,
            )
        ) {
            is CalendarResult.Available -> result.entries
            CalendarResult.PermissionRequired -> return WidgetPayload(
                environment.breakpoints.associateWith {
                    setupContent(
                        label = "day vibe",
                        headline = "calendar access needed",
                        explanation = "Day Vibe reads today's events on the device to read the day.",
                        callToAction = "tap to allow",
                    )
                },
                onClick = action,
            )
            is CalendarResult.Unavailable -> return WidgetPayload(
                environment.breakpoints.associateWith {
                    setupContent(
                        label = "day vibe",
                        headline = "calendar unavailable",
                        explanation = "The calendar could not be read on this device.",
                        callToAction = "tap for details",
                    )
                },
                onClick = action,
            )
        }

        val reading = DayVibeLogic.analyse(entries, now)
        val document = environment.content.document(WidgetType.DAY_VIBE)
        val bandKey = DayVibeLogic.stateKey(reading)
        val specialKey = DayVibeLogic.specialStateKey(reading)
        val variables = DayVibeLogic.variables(reading) { it.format(TIME_FORMAT) }
        val bookedText = "${Formatting.hoursShort(reading.bookedMinutes.toInt())} booked"
        val meetingWord = if (reading.meetingCount == 1) "meeting" else "meetings"

        val content = environment.breakpoints.associateWith { breakpoint ->
            val band = environment.session.select(
                poolKey = "dayvibe:$bandKey",
                candidates = document.pool(bandKey, environment.personality),
                policy = AntiRepeatPolicies.DAY_VIBE,
                variables = variables,
                maxChars = breakpoint.maxResponseChars,
            )?.text.orEmpty()

            val special = specialKey?.let { key ->
                environment.session.select(
                    poolKey = "dayvibe:$key",
                    candidates = document.pool(key, environment.personality),
                    policy = AntiRepeatPolicies.DAY_VIBE_SPECIAL,
                    variables = variables,
                    maxChars = breakpoint.maxResponseChars,
                )?.text
            }
            val voice = special ?: band

            val description = "${reading.meetingCount} $meetingWord today, $bookedText. $voice"

            when (breakpoint) {
                WidgetBreakpoint.TINY -> TileContent(
                    label = "day vibe",
                    heroValue = "${reading.meetingCount} mtgs",
                    contentDescription = description,
                )

                WidgetBreakpoint.COMPACT -> TileContent(
                    label = "day vibe",
                    heroValue = "${reading.meetingCount} mtgs",
                    voice = voice,
                    contentDescription = description,
                )

                WidgetBreakpoint.STANDARD, WidgetBreakpoint.WIDE -> TileContent(
                    label = "day vibe",
                    heroValue = "${reading.meetingCount} $meetingWord",
                    metric = bookedText,
                    voice = voice,
                    leading = dots(reading),
                    contentDescription = description,
                )

                WidgetBreakpoint.EXPANDED -> TileContent(
                    label = "day vibe · ${now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).lowercase()}",
                    heroValue = reading.meetingCount.toString(),
                    subhead = "$meetingWord, $bookedText",
                    pill = voice,
                    leading = dots(reading),
                    contentDescription = description,
                )
            }
        }
        return WidgetPayload(content, onClick = refreshAction(WidgetType.DAY_VIBE))
    }

    /** "Dots = meetings, max 8 then 8+." — the sheet's Day Vibe note. */
    private fun dots(reading: DayVibeReading): LeadingVisual.Dots {
        val total = maxOf(MIN_DOTS, minOf(MAX_DOTS, reading.meetingCount))
        return LeadingVisual.Dots(filled = reading.meetingCount.coerceAtMost(MAX_DOTS), total = total)
    }

    private companion object {
        const val MIN_DOTS = 6
        const val MAX_DOTS = 8
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

class DayVibeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DayVibeWidget()
}
