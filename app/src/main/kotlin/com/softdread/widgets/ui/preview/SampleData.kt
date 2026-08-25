package com.softdread.widgets.ui.preview

import com.softdread.widgets.domain.model.ColourRole
import com.softdread.widgets.domain.model.Personality
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.widgets.common.LeadingVisual
import com.softdread.widgets.widgets.common.TileChip
import com.softdread.widgets.widgets.common.TileContent

/**
 * Representative sample tiles for the three widgets that cannot show live data
 * until they have been set up.
 *
 * These values are only ever rendered behind an explicit "sample" label in the
 * gallery and detail screens. A placed widget never renders them: the real tile
 * shows a setup state instead, because inventing a screen-time figure would be
 * worse than admitting the permission is missing.
 *
 * The copy is taken from the design sheet's own widget matrix, which is where
 * these exact strings appear as the reference tiles.
 */
object SampleData {

    fun content(
        type: WidgetType,
        breakpoint: WidgetBreakpoint,
        personality: Personality = Personality.DEFAULT,
    ): TileContent = when (type) {
        WidgetType.SCREEN_TIME -> screenTime(breakpoint)
        WidgetType.DAY_VIBE -> dayVibe(breakpoint)
        WidgetType.WEATHER -> weather(breakpoint)
        WidgetType.COUNTDOWN -> countdown(breakpoint)
        else -> TileContent(
            label = type.id.replace('_', ' '),
            voice = "Sample preview",
            contentDescription = "Sample preview",
        )
    }

    private fun screenTime(breakpoint: WidgetBreakpoint) = TileContent(
        label = "screen time",
        labelDetail = "6h 30m",
        heroValue = if (breakpoint == WidgetBreakpoint.STANDARD) null else "0.7",
        subhead = if (breakpoint.isLarge) "lord of the rings trilogies" else null,
        voice = when (breakpoint) {
            WidgetBreakpoint.COMPACT -> "lord of the rings trilogies"
            WidgetBreakpoint.STANDARD, WidgetBreakpoint.WIDE ->
                "That's roughly 0.7x the theatrical Lord of the Rings trilogy."
            else -> null
        },
        pill = if (breakpoint.isLarge) "6h 30m. The rectangle has completed most of a shift." else null,
        leading = if (breakpoint == WidgetBreakpoint.STANDARD) LeadingVisual.Numeral("0.7") else null,
        contentDescription = "Sample: screen time 6 hours 30 minutes, about 0.7 of the Lord of the Rings trilogy.",
    )

    private fun dayVibe(breakpoint: WidgetBreakpoint) = TileContent(
        label = if (breakpoint.isLarge) "day vibe · wednesday" else "day vibe",
        heroValue = if (breakpoint.isLarge) "4" else "4 mtgs",
        metric = if (breakpoint == WidgetBreakpoint.STANDARD) "5h 30m booked" else null,
        subhead = if (breakpoint.isLarge) "meetings, 5h 30m booked" else null,
        voice = if (breakpoint.isLarge) null else "This day seems suspiciously busy.",
        pill = if (breakpoint.isLarge) "4 meetings. This day seems suspiciously busy." else null,
        leading = if (breakpoint.showsSecondaryMetadata) LeadingVisual.Dots(4, 6) else null,
        contentDescription = "Sample: 4 meetings today, 5 hours 30 minutes booked.",
    )

    private fun weather(breakpoint: WidgetBreakpoint) = TileContent(
        label = if (breakpoint == WidgetBreakpoint.COMPACT) "weather" else "weather, translated",
        labelDetail = if (breakpoint.isLarge) "sample city" else null,
        heroValue = "34°",
        metric = if (breakpoint.showsSecondaryMetadata) "feels 39°" else null,
        voice = if (breakpoint.isLarge) null else "outside has been preheated.",
        pill = if (breakpoint.isLarge) "34°C — outside has been preheated." else null,
        strip = if (breakpoint.isLarge) listOf(0.1f, 0.2f, 0.7f, 0.9f, 0.4f) else emptyList(),
        satelliteRole = ColourRole.AMBER,
        contentDescription = "Sample: 34 degrees, feels like 39.",
    )

    private fun countdown(breakpoint: WidgetBreakpoint) = TileContent(
        label = if (breakpoint.showsSecondaryMetadata) "countdown · vacation" else "vacation",
        heroValue = if (breakpoint.isLarge) "19" else "19 days",
        heroSuffix = if (breakpoint.isLarge) "days" else null,
        subhead = if (breakpoint.isLarge) "= 2.7 weekends" else null,
        voice = if (breakpoint.isLarge) null else "= 2.7 weekends",
        pill = if (breakpoint.isLarge) "Vacation in 19 days = 2.7 weekends." else null,
        leading = if (breakpoint == WidgetBreakpoint.STANDARD) LeadingVisual.Numeral("19") else null,
        chips = emptyList<TileChip>(),
        contentDescription = "Sample: vacation in 19 days, about 2.7 weekends.",
    )
}
