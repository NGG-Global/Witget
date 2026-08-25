package com.softdread.widgets.widgets.battery

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.softdread.widgets.data.content.pool
import com.softdread.widgets.data.device.BatteryDataSource
import com.softdread.widgets.design.SoftDreadTiles
import com.softdread.widgets.domain.logic.BatteryLogic
import com.softdread.widgets.domain.logic.BatteryReading
import com.softdread.widgets.domain.logic.Formatting
import com.softdread.widgets.domain.model.ColourRole
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.domain.selection.AntiRepeatPolicies
import com.softdread.widgets.widgets.common.LeadingVisual
import com.softdread.widgets.widgets.common.SoftDreadWidget
import com.softdread.widgets.widgets.common.TileContent
import com.softdread.widgets.widgets.common.WidgetEnvironment
import com.softdread.widgets.widgets.common.WidgetPayload
import com.softdread.widgets.widgets.common.refreshAction

/**
 * Battery Prognosis.
 *
 * The percentage is always the loudest thing on the tile — the Content Bible is
 * explicit that personality copy interprets the battery rather than replacing
 * it, and that low-battery copy must keep the need to charge obvious. The ring
 * turns clay below 10% per the design sheet, while the tile itself stays sage.
 */
class BatteryWidget : SoftDreadWidget(WidgetType.BATTERY) {

    override suspend fun buildPayload(environment: WidgetEnvironment): WidgetPayload {
        val reading = BatteryDataSource(environment.context).read()
        val stateKey = BatteryLogic.stateKey(reading)
        val document = environment.content.document(WidgetType.BATTERY)
        val candidates = document.pool(stateKey, environment.personality)
        val variables = BatteryLogic.variables(reading)
        val percentText = "${reading.percent}%"
        val estimate = reading.minutesRemaining?.let { "~ ${Formatting.hoursMinutes(it)}" }

        val ringFill = if (reading.percent < 10 && !reading.isCharging) {
            SoftDreadTiles.colours(ColourRole.CLAY, dark = environment.isDark).surface
        } else {
            null
        }

        val content = environment.breakpoints.associateWith { breakpoint ->
            val voice = environment.session.select(
                poolKey = "battery:$stateKey",
                candidates = candidates,
                policy = AntiRepeatPolicies.STATUS,
                variables = variables,
                maxChars = breakpoint.maxResponseChars,
            )?.text.orEmpty()

            val chargingNote = if (reading.isCharging) "charging" else null
            TileContent(
                label = if (breakpoint.isLarge) "battery prognosis" else "battery",
                labelDetail = chargingNote,
                heroValue = if (breakpoint == WidgetBreakpoint.EXPANDED) null else percentText,
                metric = estimate.takeIf { breakpoint.showsSecondaryMetadata && !breakpoint.isLarge },
                voice = voice.takeIf { breakpoint != WidgetBreakpoint.EXPANDED },
                pill = if (breakpoint.isLarge) "$percentText — ${voice.replaceFirstChar { it.lowercase() }}" else null,
                leading = LeadingVisual.Ring(
                    fraction = reading.percent / 100f,
                    fillColour = ringFill,
                    centreLabel = percentText.takeIf { breakpoint.isLarge },
                    centreDetail = estimate?.takeIf { breakpoint.isLarge },
                ).takeIf { breakpoint.showsCircle },
                contentDescription = describe(reading, voice, estimate),
            )
        }
        return WidgetPayload(content, onClick = refreshAction(WidgetType.BATTERY))
    }

    private fun describe(reading: BatteryReading, voice: String, estimate: String?): String = buildString {
        append("Battery ${reading.percent} percent")
        if (reading.isCharging) append(", charging")
        estimate?.let { append(", about ${it.removePrefix("~ ")} remaining") }
        if (voice.isNotBlank()) append(". $voice")
    }
}

class BatteryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BatteryWidget()
}
