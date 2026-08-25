package com.softdread.widgets.widgets.common

import androidx.compose.ui.graphics.Color
import com.softdread.widgets.domain.model.ColourRole

/** The leading visual on the left of a wide tile, or above the value on a small one. */
sealed interface LeadingVisual {
    /** The data circle used by Battery and Time Progress. */
    data class Ring(
        val fraction: Float,
        val fillColour: Color? = null,
        val centreLabel: String? = null,
        val centreDetail: String? = null,
    ) : LeadingVisual

    /** The oversized numeral that leads Screen Time and Countdown. */
    data class Numeral(val text: String) : LeadingVisual

    /** Day Vibe's meeting dots. */
    data class Dots(val filled: Int, val total: Int) : LeadingVisual

    /** A plain disc: the weather sky, or the 8 ball. */
    data class Disc(val colour: Color, val label: String? = null) : LeadingVisual
}

/** A metadata chip, used by Daily Joke. */
data class TileChip(val text: String, val emphasised: Boolean = false)

/** One labelled progress bar, used by Time Progress. */
data class TileBar(val label: String, val fraction: Float, val valueText: String, val colourRole: ColourRole)

/**
 * Everything a tile renders, independent of size.
 *
 * Each widget produces one of these; [SoftDreadTile] decides what survives at
 * each breakpoint. That split is what makes responsive behaviour a content
 * decision rather than five hand-built layouts per widget, and it is why the
 * compact battery tile drops the remaining-time metric while the large one keeps
 * it.
 */
data class TileContent(
    /** MICRO-LABEL, rendered uppercase. */
    val label: String,
    /** Optional right-aligned label detail: "06:00", "TEL AVIV". */
    val labelDetail: String? = null,
    /** The headline value: "23%", "34", "0.7", "4 mtgs". */
    val heroValue: String? = null,
    /** A unit that trails the hero value at a smaller size: "days". */
    val heroSuffix: String? = null,
    /** A secondary metric that must stay visible: "feels 39", "~ 2h 10m". */
    val metric: String? = null,
    /** The line under the hero on large tiles: "lord of the rings trilogies". */
    val subhead: String? = null,
    /** The personality line from the Content Bible. */
    val voice: String? = null,
    /** The 4x4 pill copy. The sheet allows a pill at 4x4 only. */
    val pill: String? = null,
    val chips: List<TileChip> = emptyList(),
    val bars: List<TileBar> = emptyList(),
    /**
     * A segmented strip of 0..1 values, drawn above the hero on the largest
     * tile. Weather plots rain probability for the next few hours here.
     */
    val strip: List<Float> = emptyList(),
    val leading: LeadingVisual? = null,
    /** "tap to ask again" and similar affordance text. */
    val callToAction: String? = null,
    /** Spoken description for TalkBack, assembled from the real values. */
    val contentDescription: String,
    /** Overrides the tile's satellite colour (Weather uses it for the sky). */
    val satelliteRole: ColourRole? = null,
    /** True when the tile needs setup rather than data. */
    val isSetupState: Boolean = false,
)
