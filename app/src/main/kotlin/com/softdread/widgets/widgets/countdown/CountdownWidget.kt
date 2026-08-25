package com.softdread.widgets.widgets.countdown

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.softdread.widgets.data.content.pool
import com.softdread.widgets.domain.logic.CountdownLogic
import com.softdread.widgets.domain.logic.CountdownReading
import com.softdread.widgets.domain.logic.Formatting
import com.softdread.widgets.domain.model.ColourRole
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.domain.selection.AntiRepeatPolicies
import com.softdread.widgets.widgets.common.LeadingVisual
import com.softdread.widgets.widgets.common.SoftDreadWidget
import com.softdread.widgets.widgets.common.TileBar
import com.softdread.widgets.widgets.common.TileContent
import com.softdread.widgets.widgets.common.WidgetEnvironment
import com.softdread.widgets.widgets.common.WidgetPayload
import com.softdread.widgets.widgets.common.openAppAction
import com.softdread.widgets.widgets.common.setupContent
import java.time.ZoneId

/**
 * Countdown in Perspective.
 *
 * Each instance keeps its own title, target and framing unit, so a Japan trip
 * and a birthday coexist without touching each other's configuration or
 * anti-repeat history.
 *
 * The design sheet's rule for this tile is "two numbers, always: literal days,
 * then the relatable unit", which is why the perspective line is derived
 * arithmetically rather than being left to the copy pool — the Bible's framing
 * lines then sit on top of a number the user can already trust.
 */
class CountdownWidget : SoftDreadWidget(WidgetType.COUNTDOWN) {

    override suspend fun buildPayload(environment: WidgetEnvironment): WidgetPayload {
        val config = environment.config
        val action = openAppAction(environment.context, WidgetType.COUNTDOWN, config.appWidgetId)

        if (!config.isConfigured) {
            return WidgetPayload(
                environment.breakpoints.associateWith {
                    setupContent(
                        label = "countdown",
                        headline = "nothing to count yet",
                        explanation = "Choose what you are waiting for and when it happens.",
                        callToAction = "tap to set up",
                    )
                },
                onClick = action,
            )
        }

        val zone = config.countdownZoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        val reading = CountdownLogic.read(
            title = config.countdownTitle,
            targetEpochMillis = config.countdownTargetEpochMillis,
            clock = environment.clock,
            zoneId = zone,
        )

        if (reading.hasPassed) {
            // The Content Bible writes no copy for an elapsed countdown, so the
            // tile states the fact plainly rather than borrowing a line that was
            // written for a future event.
            val elapsed = elapsedLabel(reading)
            return WidgetPayload(
                environment.breakpoints.associateWith { breakpoint ->
                    TileContent(
                        label = config.countdownTitle,
                        heroValue = elapsed,
                        voice = "${config.countdownTitle} has been and gone.",
                        pill = if (breakpoint.isLarge) "${config.countdownTitle} was $elapsed ago." else null,
                        contentDescription = "${config.countdownTitle} was $elapsed ago.",
                    )
                },
                onClick = action,
            )
        }

        val document = environment.content.document(WidgetType.COUNTDOWN)
        val stateKey = CountdownLogic.stateKey(reading)
        val variables = CountdownLogic.variables(reading)
        val candidates = document.pool(stateKey, environment.personality)
        val perspective = CountdownLogic.perspectiveLine(reading, config.countdownUnit)
        val daysText = daysLabel(reading)

        val content = environment.breakpoints.associateWith { breakpoint ->
            val voice = environment.session.select(
                poolKey = "countdown:$stateKey",
                candidates = candidates,
                policy = AntiRepeatPolicies.COUNTDOWN,
                variables = variables,
                maxChars = breakpoint.maxResponseChars,
                periodKey = "countdown:${environment.clock.now().toEpochMilli() / MILLIS_PER_DAY}",
            )?.text.orEmpty()

            val description = "${config.countdownTitle} in $daysText, about $perspective. $voice"

            when (breakpoint) {
                WidgetBreakpoint.TINY -> TileContent(
                    label = config.countdownTitle,
                    heroValue = daysText,
                    contentDescription = description,
                )

                WidgetBreakpoint.COMPACT -> TileContent(
                    label = config.countdownTitle,
                    heroValue = daysText,
                    voice = "= $perspective",
                    contentDescription = description,
                )

                WidgetBreakpoint.STANDARD, WidgetBreakpoint.WIDE -> TileContent(
                    label = "countdown · ${config.countdownTitle}",
                    voice = voice,
                    leading = LeadingVisual.Numeral(leadNumber(reading)),
                    contentDescription = description,
                )

                WidgetBreakpoint.EXPANDED -> TileContent(
                    label = "countdown · ${config.countdownTitle}",
                    heroValue = leadNumber(reading),
                    heroSuffix = unitWord(reading),
                    subhead = "= $perspective",
                    bars = elapsedBar(config.countdownCreatedAtEpochMillis, reading, environment),
                    pill = voice,
                    contentDescription = description,
                )
            }
        }
        return WidgetPayload(content, onClick = action)
    }

    /**
     * A single honest progress bar: how far the wait has come since the
     * countdown was created. Instances created before this was recorded show no
     * bar rather than a made-up one.
     */
    private fun elapsedBar(
        createdAtEpochMillis: Long,
        reading: CountdownReading,
        environment: WidgetEnvironment,
    ): List<TileBar> {
        if (createdAtEpochMillis <= 0L) return emptyList()
        val now = environment.clock.now().toEpochMilli()
        val target = reading.target.toInstant().toEpochMilli()
        val total = (target - createdAtEpochMillis).toDouble()
        if (total <= 0.0) return emptyList()
        val fraction = ((now - createdAtEpochMillis) / total).coerceIn(0.0, 1.0)
        return listOf(
            TileBar(
                label = "waited",
                fraction = fraction.toFloat(),
                valueText = "${Formatting.percent(fraction * 100)}%",
                colourRole = ColourRole.AMBER,
            ),
        )
    }

    private fun leadNumber(reading: CountdownReading): String = when {
        reading.totalHours < 1 -> Formatting.integer(reading.totalMinutes)
        reading.totalDays < 1 -> Formatting.integer(reading.totalHours)
        else -> Formatting.integer(reading.totalDays)
    }

    private fun unitWord(reading: CountdownReading): String = when {
        reading.totalHours < 1 -> if (reading.totalMinutes == 1L) "minute" else "minutes"
        reading.totalDays < 1 -> if (reading.totalHours == 1L) "hour" else "hours"
        else -> if (reading.totalDays == 1L) "day" else "days"
    }

    private fun daysLabel(reading: CountdownReading): String =
        "${leadNumber(reading)} ${unitWord(reading)}"

    private fun elapsedLabel(reading: CountdownReading): String = when {
        reading.totalDays >= 1 -> "${Formatting.integer(reading.totalDays)} ${if (reading.totalDays == 1L) "day" else "days"}"
        reading.totalHours >= 1 -> "${Formatting.integer(reading.totalHours)} ${if (reading.totalHours == 1L) "hour" else "hours"}"
        else -> "${Formatting.integer(reading.totalMinutes)} min"
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}

class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()
}
