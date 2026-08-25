package com.softdread.widgets.widgets.common

import com.softdread.widgets.design.TileColours
import com.softdread.widgets.domain.model.ColourRole
import com.softdread.widgets.domain.model.WidgetBreakpoint

/**
 * Per-widget circle placement, transcribed from the design sheet's widget matrix.
 *
 * The 4x4 diameters are the sheet's pixel values divided by its 368px tile —
 * the largest circle in the whole matrix is clay's at 300px, ratio 0.82. An
 * earlier transcription doubled these, which buried the expanded tiles under
 * their own circles and put cream text on a cream field; the renderer's
 * snapshot dump is what finally made that visible, so treat these numbers as
 * measured, not tunable.
 *
 * The sheet's second unbreakable rule is "exactly one oversized circle per tile,
 * cropped by at least one edge; optional single satellite". Every entry below is
 * read off the corresponding tile in the sheet and converted from its pixel
 * offsets into the short-side ratios [TileArt] draws with.
 *
 * Battery and Time Progress carry no field circle at their smallest size — in
 * the sheet those two tiles lead with a data circle instead, which is the one
 * circle they are allowed.
 */
object TileGeometry {

    fun circle(
        role: ColourRole,
        breakpoint: WidgetBreakpoint,
        colours: TileColours,
    ): CircleSpec? {
        if (!breakpoint.showsCircle) return null
        val cream = colours.circle
        val tint = colours.circleTint
        return when (role) {
            ColourRole.CLAY -> when (breakpoint) {
                WidgetBreakpoint.COMPACT -> CircleSpec(cream, 0.74f, CircleAnchor.BOTTOM_END, 0.30f)
                WidgetBreakpoint.EXPANDED -> CircleSpec(cream, 0.82f, CircleAnchor.BOTTOM_END, 0.43f)
                else -> CircleSpec(tint, 0.84f, CircleAnchor.BOTTOM_START, 0.34f)
            }
            ColourRole.AMBER -> when (breakpoint) {
                WidgetBreakpoint.COMPACT -> CircleSpec(cream, 0.74f, CircleAnchor.BOTTOM_END, 0.30f)
                WidgetBreakpoint.EXPANDED -> CircleSpec(cream, 0.79f, CircleAnchor.BOTTOM_START, 0.31f)
                else -> CircleSpec(cream, 0.95f, CircleAnchor.BOTTOM_END, 0.34f)
            }
            ColourRole.SAGE -> when (breakpoint) {
                // The 2x2 battery tile is all ring; the sheet gives it no field circle.
                WidgetBreakpoint.COMPACT -> null
                WidgetBreakpoint.EXPANDED -> CircleSpec(tint, 0.73f, CircleAnchor.BOTTOM_START, 0.26f)
                else -> CircleSpec(tint, 0.84f, CircleAnchor.TOP_END, 0.32f)
            }
            ColourRole.CREAM -> when (breakpoint) {
                WidgetBreakpoint.COMPACT -> CircleSpec(colours.contrastCircle, 0.63f, CircleAnchor.TOP_END, 0.24f)
                WidgetBreakpoint.EXPANDED -> CircleSpec(colours.contrastCircle, 0.68f, CircleAnchor.TOP_END, 0.28f)
                else -> CircleSpec(colours.contrastCircle, 0.78f, CircleAnchor.BOTTOM_START, 0.24f)
            }
            ColourRole.EMBER -> when (breakpoint) {
                WidgetBreakpoint.COMPACT -> CircleSpec(colours.satellite, 0.58f, CircleAnchor.TOP_END, 0.20f)
                WidgetBreakpoint.EXPANDED -> CircleSpec(colours.satellite, 0.71f, CircleAnchor.TOP_END, 0.28f)
                else -> CircleSpec(colours.satellite, 0.84f, CircleAnchor.TOP_START, 0.22f)
            }
            ColourRole.SLATE -> when (breakpoint) {
                WidgetBreakpoint.COMPACT -> CircleSpec(tint, 0.70f, CircleAnchor.BOTTOM_START, 0.26f)
                WidgetBreakpoint.EXPANDED -> CircleSpec(tint, 0.79f, CircleAnchor.BOTTOM_START, 0.36f)
                else -> CircleSpec(tint, 0.89f, CircleAnchor.BOTTOM_END, 0.32f)
            }
            ColourRole.INK -> when (breakpoint) {
                // The compact year tile is a ring and a number; no field circle.
                WidgetBreakpoint.COMPACT -> null
                WidgetBreakpoint.EXPANDED -> CircleSpec(colours.circle, 0.68f, CircleAnchor.BOTTOM_START, 0.28f)
                else -> CircleSpec(colours.circle, 0.84f, CircleAnchor.TOP_END, 0.32f)
            }
            // The 8 ball tile: the ball IS its one circle, so the field stays
            // clean. A deliberate deviation from the sheet's night tile, at the
            // owner's direction — the widget should read as the actual object.
            ColourRole.NIGHT -> null
        }
    }

    /**
     * The optional satellite. The sheet places one on the 4x4 tiles of Screen
     * Time, Daily Joke, Day Vibe and Countdown only, always at or below 0.4x the
     * short side.
     */
    fun satellite(
        role: ColourRole,
        breakpoint: WidgetBreakpoint,
        colours: TileColours,
    ): SatelliteSpec? {
        if (breakpoint != WidgetBreakpoint.EXPANDED) return null
        return when (role) {
            ColourRole.CLAY -> SatelliteSpec(colours.satellite, 0.18f, 0.87f, 0.71f)
            ColourRole.AMBER -> SatelliteSpec(colours.satellite, 0.16f, 0.85f, 0.25f)
            ColourRole.CREAM -> SatelliteSpec(colours.satellite, 0.14f, 0.13f, 0.70f)
            ColourRole.SLATE -> SatelliteSpec(colours.satellite, 0.17f, 0.86f, 0.27f)
            else -> null
        }
    }
}
