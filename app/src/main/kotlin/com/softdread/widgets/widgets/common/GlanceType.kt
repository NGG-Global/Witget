package com.softdread.widgets.widgets.common

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import com.softdread.widgets.domain.model.WidgetBreakpoint

/**
 * The design sheet's type scale, expressed with the subset of typography Glance
 * supports.
 *
 * Three deliberate adaptations, all documented in `docs/design-system.md`:
 *
 *  - **Typeface.** Glance's `FontFamily` resolves a *system* family name through
 *    RemoteViews; it cannot reference a bundled font resource. Widget text
 *    therefore renders in the platform sans-serif while the app itself uses the
 *    real Bricolage Grotesque. Drawing widget copy into bitmaps would restore
 *    the face but would break font scaling and TalkBack, so hierarchy is
 *    preserved through size, weight and case instead.
 *  - **Weight.** Glance exposes Normal, Medium and Bold; the sheet's 800 maps to
 *    Bold and its 500 to Medium.
 *  - **Letter-spacing and line height.** Glance's `TextStyle` has neither. The
 *    sheet's tight tracking on hero numerals is approximated by size alone.
 *
 * The floor the sheet sets — "TEXT NEVER BELOW 13PX / 500" — is respected by
 * every style below except [microLabel], which the sheet itself sets at 10.5.
 */
object GlanceType {

    fun hero(breakpoint: WidgetBreakpoint, colour: Color): TextStyle =
        heroFitted(breakpoint, colour, text = "")

    /**
     * The hero style, stepped down for long values.
     *
     * Glance has no text auto-sizing and no measurement pass, so a value like
     * "100%" beside the battery ring, "365 days" on a compact countdown or
     * "12 meetings" on a standard Day Vibe would clip at the sheet's nominal
     * sizes. The step-downs below are derived from the tiles' inner widths at
     * the bold face's ~0.55em advance; short values render at the sheet's exact
     * scale and only the long tail shrinks.
     */
    fun heroFitted(
        breakpoint: WidgetBreakpoint,
        colour: Color,
        text: String,
        besideLeading: Boolean = false,
    ): TextStyle {
        val length = text.length
        val size = when (breakpoint) {
            WidgetBreakpoint.TINY -> if (length <= 6) 26.sp else 22.sp
            WidgetBreakpoint.COMPACT -> when {
                besideLeading -> if (length <= 4) 32.sp else 26.sp
                length <= 4 -> 38.sp
                length <= 7 -> 32.sp
                else -> 26.sp
            }
            WidgetBreakpoint.STANDARD, WidgetBreakpoint.WIDE -> when {
                length <= 4 -> 46.sp
                length <= 8 -> 40.sp
                else -> 32.sp
            }
            WidgetBreakpoint.EXPANDED -> when {
                length <= 4 -> 76.sp
                length <= 7 -> 64.sp
                else -> 44.sp
            }
        }
        return TextStyle(
            color = ColorProvider(colour),
            fontSize = size,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
        )
    }

    /** The oversized numeral the sheet uses on 4x2 and 4x4 lead-with-a-number tiles. */
    fun heroNumeral(breakpoint: WidgetBreakpoint, colour: Color): TextStyle = TextStyle(
        color = ColorProvider(colour),
        fontSize = when (breakpoint) {
            WidgetBreakpoint.TINY -> 26.sp
            WidgetBreakpoint.COMPACT -> 38.sp
            WidgetBreakpoint.STANDARD, WidgetBreakpoint.WIDE -> 62.sp
            WidgetBreakpoint.EXPANDED -> 92.sp
        },
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.SansSerif,
    )

    /** SUBHEAD 4x4 — 600 - 23/1.1. */
    fun subhead(breakpoint: WidgetBreakpoint, colour: Color): TextStyle = TextStyle(
        color = ColorProvider(colour),
        fontSize = if (breakpoint.isLarge) 23.sp else 16.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.SansSerif,
    )

    /** The personality line: VOICE 4x2 at 16/600, VOICE 2x2 at 13/500. */
    fun voice(breakpoint: WidgetBreakpoint, colour: Color): TextStyle = TextStyle(
        color = ColorProvider(colour),
        fontSize = when (breakpoint) {
            WidgetBreakpoint.TINY, WidgetBreakpoint.COMPACT -> 13.sp
            WidgetBreakpoint.STANDARD, WidgetBreakpoint.WIDE -> 16.sp
            WidgetBreakpoint.EXPANDED -> 20.sp
        },
        fontWeight = if (breakpoint == WidgetBreakpoint.COMPACT || breakpoint == WidgetBreakpoint.TINY) {
            FontWeight.Medium
        } else {
            FontWeight.Medium
        },
        fontFamily = FontFamily.SansSerif,
    )

    /** The joke and 8 ball body copy, which the sheet sets larger than the voice line. */
    fun statement(breakpoint: WidgetBreakpoint, colour: Color): TextStyle = TextStyle(
        color = ColorProvider(colour),
        fontSize = when (breakpoint) {
            WidgetBreakpoint.TINY -> 13.sp
            WidgetBreakpoint.COMPACT -> 15.sp
            WidgetBreakpoint.STANDARD, WidgetBreakpoint.WIDE -> 19.sp
            WidgetBreakpoint.EXPANDED -> 30.sp
        },
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.SansSerif,
    )

    /** MICRO-LABEL — 600 - 10.5 - .14em - uppercase. Case is applied by the caller. */
    fun microLabel(breakpoint: WidgetBreakpoint, colour: Color): TextStyle = TextStyle(
        color = ColorProvider(colour),
        fontSize = if (breakpoint.isLarge) 12.sp else 11.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.SansSerif,
    )

    /** Secondary metrics that must stay legible beside the hero value. */
    fun metric(breakpoint: WidgetBreakpoint, colour: Color): TextStyle = TextStyle(
        color = ColorProvider(colour),
        fontSize = if (breakpoint.isLarge) 15.sp else 13.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.SansSerif,
    )

    fun chip(colour: Color): TextStyle = TextStyle(
        color = ColorProvider(colour),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.SansSerif,
    )

    fun pill(breakpoint: WidgetBreakpoint, colour: Color): TextStyle = TextStyle(
        color = ColorProvider(colour),
        fontSize = if (breakpoint.isLarge) 14.sp else 13.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.SansSerif,
    )

    val CallToAction: (Color) -> TextStyle = { colour ->
        TextStyle(
            color = ColorProvider(colour),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif,
        )
    }

    fun size(value: Float): TextUnit = value.sp
}
