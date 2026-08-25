package com.softdread.widgets.domain.model

/**
 * Home-screen size breakpoints.
 *
 * The dp values come straight from the design sheet's grid note
 * (cell 84.5 x 84.5, gutter 10): 2x2 = 179, 4x2 = 368 x 179, 4x4 = 368, plus the
 * two extension sizes in the "scaling & extension" panel.
 *
 * [maxResponseChars] is the copy budget for that tile. The design sheet gives
 * 2x2 <= 34, 4x2 <= 62, 4x4 <= 96 and 5x2 <= 78; the Content Bible's
 * compact-layout rule requires selecting a shorter eligible string rather than
 * truncating, which [com.softdread.widgets.domain.selection.ResponseSelector]
 * implements against this budget.
 */
enum class WidgetBreakpoint(
    val widthDp: Int,
    val heightDp: Int,
    val maxResponseChars: Int,
    val cornerRadiusDp: Int,
    val paddingVerticalDp: Int,
    val paddingHorizontalDp: Int,
    val showsCircle: Boolean,
    val showsPill: Boolean,
) {
    /** 2x1 (179 x 85): label + value only, radius 28, no circle. */
    TINY(179, 85, 45, 28, 10, 14, showsCircle = false, showsPill = false),

    /** 2x2 (179): the smallest tile that carries a personality line. */
    COMPACT(179, 179, 34, 34, 14, 15, showsCircle = true, showsPill = false),

    /** 4x2 (368 x 179). */
    STANDARD(368, 179, 62, 34, 18, 22, showsCircle = true, showsPill = false),

    /** 5x2 (462 x 179): 4x2 rules, circle 20% larger, copy <= 78 chars. */
    WIDE(462, 179, 78, 34, 18, 22, showsCircle = true, showsPill = false),

    /** 4x4 (368): the only size that sets copy in a cream pill. */
    EXPANDED(368, 368, 96, 34, 20, 22, showsCircle = true, showsPill = true),
    ;

    val isLarge: Boolean get() = this == EXPANDED

    /** True for tiles wide enough to carry secondary metadata (chips, metrics). */
    val showsSecondaryMetadata: Boolean get() = this != TINY && this != COMPACT

    companion object {
        /**
         * Picks the breakpoint for an actual widget size. Glance hands us the
         * size it resolved from [androidx.glance.appwidget.SizeMode.Responsive],
         * but launchers can report values a few dp off the requested size, so
         * this matches on thresholds rather than equality.
         */
        fun forSize(widthDp: Float, heightDp: Float): WidgetBreakpoint = when {
            heightDp < 130f -> if (widthDp < 260f) TINY else STANDARD
            widthDp < 260f -> COMPACT
            heightDp >= 260f -> EXPANDED
            widthDp >= 430f -> WIDE
            else -> STANDARD
        }
    }
}
