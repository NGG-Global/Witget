package com.softdread.widgets.widgets.screentime

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.softdread.widgets.data.content.pool
import com.softdread.widgets.data.device.ScreenTimeDataSource
import com.softdread.widgets.data.device.ScreenTimeResult
import com.softdread.widgets.domain.logic.EquivalencyMatch
import com.softdread.widgets.domain.logic.Formatting
import com.softdread.widgets.domain.logic.ScreenTimeLogic
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
import kotlin.random.Random

/**
 * Screen Time Translator.
 *
 * Two authoritative rules meet on this tile. The design sheet leads with the
 * equivalency ratio as the hero numeral; the Content Bible requires the real
 * metric to stay visible. Both are honoured: the ratio is the hero, and the
 * literal usage rides in the micro-label row at every size, which is also where
 * it appears in the tile's spoken description.
 *
 * Without Usage Access the tile says so plainly. It never renders a zero.
 */
class ScreenTimeWidget : SoftDreadWidget(WidgetType.SCREEN_TIME) {

    override suspend fun buildPayload(environment: WidgetEnvironment): WidgetPayload {
        val source = ScreenTimeDataSource(environment.context)
        val action = openAppAction(environment.context, WidgetType.SCREEN_TIME, environment.config.appWidgetId)

        val minutes = when (val result = source.readToday(environment.clock.zone())) {
            is ScreenTimeResult.Available -> result.minutesToday
            ScreenTimeResult.PermissionRequired -> return WidgetPayload(
                environment.breakpoints.associateWith {
                    setupContent(
                        label = "screen time",
                        headline = "usage access needed",
                        explanation = "Android keeps screen time behind a separate permission.",
                        callToAction = "tap to set up",
                    )
                },
                onClick = action,
            )
            is ScreenTimeResult.Unavailable -> return WidgetPayload(
                environment.breakpoints.associateWith {
                    setupContent(
                        label = "screen time",
                        headline = "no usage data yet",
                        explanation = "This device has not reported foreground usage today.",
                        callToAction = "tap to retry",
                    )
                },
                onClick = action,
            )
        }

        val document = environment.content.document(WidgetType.SCREEN_TIME)
        val stateKey = ScreenTimeLogic.stateKey(minutes)
        val usageText = Formatting.hoursShort(minutes)

        // The seven-day equivalency cooldown lives in its own pool so it is
        // independent of which commentary line happened to be shown.
        val excludedUnits = environment.session.cooledDownIds(
            EQUIVALENCY_POOL,
            AntiRepeatPolicies.SCREEN_TIME_EQUIVALENCY,
        )
        val match = ScreenTimeLogic.chooseEquivalency(
            usageMinutes = minutes,
            units = document.equivalencyUnits,
            category = environment.config.equivalencyCategory,
            excludedUnitIds = excludedUnits,
            random = Random(environment.clock.now().toEpochMilli()),
        )
        match?.let {
            environment.session.record(EQUIVALENCY_POOL, it.unit.id, AntiRepeatPolicies.SCREEN_TIME_EQUIVALENCY)
        }

        val variables = ScreenTimeLogic.variables(minutes, match)
        val bandCandidates = document.pool(stateKey, environment.personality)
        val framingCandidates = document.pool(ScreenTimeLogic.FRAMING_STATE_KEY, environment.personality)

        val content = environment.breakpoints.associateWith { breakpoint ->
            val commentary = environment.session.select(
                poolKey = "screentime:$stateKey",
                candidates = bandCandidates,
                policy = AntiRepeatPolicies.SCREEN_TIME_COMMENTARY,
                variables = variables,
                maxChars = breakpoint.maxResponseChars,
            )?.text.orEmpty()

            val framing = environment.session.select(
                poolKey = "screentime:framing",
                candidates = framingCandidates,
                policy = AntiRepeatPolicies.SCREEN_TIME_FRAMING,
                variables = variables,
                maxChars = breakpoint.maxResponseChars,
            )?.text

            build(breakpoint, usageText, match, commentary, framing)
        }
        // Data is live: a tap re-reads today's usage. Setup states above keep
        // the open-app action so the user lands on the permission explanation.
        return WidgetPayload(content, onClick = refreshAction(WidgetType.SCREEN_TIME))
    }

    private fun build(
        breakpoint: WidgetBreakpoint,
        usageText: String,
        match: EquivalencyMatch?,
        commentary: String,
        framing: String?,
    ): TileContent {
        val ratio = match?.displayRatio
        val description = buildString {
            append("Screen time today $usageText")
            match?.let { append(", about ${it.displayRatio} of ${it.unit.label}") }
            if (commentary.isNotBlank()) append(". $commentary")
        }

        return when (breakpoint) {
            // The 2x1 tile is label and value only; the value is the real metric.
            WidgetBreakpoint.TINY -> TileContent(
                label = "screen time",
                heroValue = usageText,
                contentDescription = description,
            )

            WidgetBreakpoint.COMPACT -> TileContent(
                label = "screen time",
                labelDetail = usageText,
                heroValue = ratio?.let { if (match.asPercentage) it else "$it x" } ?: usageText,
                voice = match?.unit?.label ?: commentary,
                contentDescription = description,
            )

            WidgetBreakpoint.STANDARD, WidgetBreakpoint.WIDE -> TileContent(
                label = "screen time",
                labelDetail = usageText,
                voice = framing ?: commentary,
                leading = ratio?.let { LeadingVisual.Numeral(it.removeSuffix("%")) },
                contentDescription = description,
            )

            WidgetBreakpoint.EXPANDED -> TileContent(
                label = "screen time",
                labelDetail = usageText,
                heroValue = ratio ?: usageText,
                subhead = match?.unit?.label,
                pill = commentary,
                contentDescription = description,
            )
        }
    }

    private companion object {
        const val EQUIVALENCY_POOL = "screentime:units"
    }
}

class ScreenTimeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScreenTimeWidget()
}
