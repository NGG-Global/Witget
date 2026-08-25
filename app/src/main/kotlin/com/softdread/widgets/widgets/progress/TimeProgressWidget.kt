package com.softdread.widgets.widgets.progress

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.softdread.widgets.data.content.pool
import com.softdread.widgets.data.prefs.ProgressScope
import com.softdread.widgets.design.SoftDreadTiles
import com.softdread.widgets.domain.logic.Formatting
import com.softdread.widgets.domain.logic.ProgressReading
import com.softdread.widgets.domain.logic.TimeProgressLogic
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
import com.softdread.widgets.widgets.common.refreshAction
import kotlin.random.Random

/**
 * Time Progress.
 *
 * Percentages are computed from real period boundaries in the user's zone, so
 * leap years, 28-day Februaries, DST-shortened days and the user's own
 * first-day-of-week setting all fall out of `java.time` rather than out of
 * constants.
 *
 * The Content Bible allows a scope-specific alternate pool 35% of the time on
 * medium and large tiles, and forbids repeating a response in adjacent periods —
 * a line shown for week 35 is excluded for week 36. Both are applied here.
 */
class TimeProgressWidget : SoftDreadWidget(WidgetType.TIME_PROGRESS) {

    override suspend fun buildPayload(environment: WidgetEnvironment): WidgetPayload {
        val scope = environment.config.progressScope
        val weekStartsOnMonday = environment.preferences.weekStartsOnMonday
        val reading = TimeProgressLogic.read(scope, environment.clock, weekStartsOnMonday)
        val document = environment.content.document(WidgetType.TIME_PROGRESS)
        val variables = TimeProgressLogic.variables(reading)
        val percentText = "${reading.percent.toInt()}%"
        val previousPeriod = TimeProgressLogic.previousPeriodKey(reading, weekStartsOnMonday)

        // Week, month and year at once on the wider tiles, exactly as the sheet
        // draws them; the compact tile shows only the configured scope.
        val allBars = listOf(ProgressScope.WEEK, ProgressScope.MONTH, ProgressScope.YEAR).map { barScope ->
            val barReading = TimeProgressLogic.read(barScope, environment.clock, weekStartsOnMonday)
            TileBar(
                label = barReading.label,
                fraction = (barReading.percent / 100.0).toFloat(),
                valueText = "${barReading.percent.toInt()}%",
                colourRole = when (barScope) {
                    ProgressScope.WEEK -> ColourRole.SAGE
                    ProgressScope.MONTH -> ColourRole.EMBER
                    else -> ColourRole.AMBER
                },
            )
        }

        val content = environment.breakpoints.associateWith { breakpoint ->
            val stateKey = chooseStateKey(reading, scope, breakpoint, environment)
            val voice = environment.session.select(
                poolKey = "progress:${scope.key}:$stateKey",
                candidates = document.pool(stateKey, environment.personality),
                policy = AntiRepeatPolicies.TIME_PROGRESS,
                variables = variables,
                maxChars = breakpoint.maxResponseChars,
                periodKey = reading.periodKey,
                adjacentPeriodKeys = setOf(previousPeriod),
            )?.text.orEmpty()

            val description = "${reading.label} is $percentText complete. $voice"

            when (breakpoint) {
                WidgetBreakpoint.TINY -> TileContent(
                    label = reading.label,
                    heroValue = percentText,
                    contentDescription = description,
                )

                WidgetBreakpoint.COMPACT -> TileContent(
                    label = reading.label,
                    heroValue = percentText,
                    voice = voice,
                    leading = LeadingVisual.Ring(
                        fraction = (reading.percent / 100.0).toFloat(),
                        fillColour = SoftDreadTiles.colours(ColourRole.AMBER, dark = environment.isDark).surface,
                    ),
                    contentDescription = description,
                )

                WidgetBreakpoint.STANDARD, WidgetBreakpoint.WIDE -> TileContent(
                    label = "time progress",
                    bars = allBars,
                    voice = voice,
                    contentDescription = "$description ${allBars.joinToString(", ") { "${it.label} ${it.valueText}" }}.",
                )

                WidgetBreakpoint.EXPANDED, WidgetBreakpoint.HERO -> TileContent(
                    label = "time progress · ${reading.headline}",
                    bars = allBars,
                    pill = voice,
                    contentDescription = "$description ${allBars.joinToString(", ") { "${it.label} ${it.valueText}" }}.",
                )
            }
        }
        return WidgetPayload(content, onClick = refreshAction(WidgetType.TIME_PROGRESS))
    }

    /**
     * The Bible's scope-alternate rule: on medium and large tiles there is a 35%
     * chance of using the scope-specific pool instead of the percent band. The
     * draw is seeded from the period so it does not flicker between redraws
     * inside the same period.
     */
    private fun chooseStateKey(
        reading: ProgressReading,
        scope: ProgressScope,
        breakpoint: WidgetBreakpoint,
        environment: WidgetEnvironment,
    ): String {
        val bandKey = TimeProgressLogic.stateKey(reading.percent)
        if (!breakpoint.showsSecondaryMetadata) return bandKey
        val seed = reading.periodKey.hashCode().toLong() xor environment.config.appWidgetId.toLong()
        return if (Random(seed).nextFloat() < SCOPE_ALTERNATE_PROBABILITY) scope.stateKey else bandKey
    }

    private companion object {
        const val SCOPE_ALTERNATE_PROBABILITY = 0.35f
    }
}

class TimeProgressWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimeProgressWidget()
}
