package com.softdread.widgets.design

import com.google.common.truth.Truth.assertThat
import com.softdread.widgets.domain.model.ColourRole
import androidx.compose.ui.graphics.Color
import org.junit.Test

/**
 * Every colour the tiles actually render, checked against WCAG AA.
 *
 * The first implementation muted text with a fixed opacity per tile, which
 * looked fine on the dark fields and was unreadable on the mid-tone ones — 46 of
 * 96 pairings failed. These tests exist so that cannot happen again silently:
 * they cover all eight widgets, both light and dark, and all four theme packs,
 * including the three nobody has ever looked at.
 */
class ContrastTest {

    /** A shape that must remain visible, but is not required to read content. */
    private val PERCEPTIBLE = 1.25

    private fun assertAA(
        label: String,
        foreground: Color,
        background: Color,
        minimum: Double,
    ) {
        val ratio = Contrast.ratio(foreground, background)
        assertThat("$label = ${"%.2f".format(ratio)}:1 (needs $minimum:1)")
            .isNotEmpty()
        assertWithRatio(label, ratio, minimum)
    }

    private fun assertWithRatio(label: String, ratio: Double, minimum: Double) {
        if (ratio < minimum) {
            throw AssertionError("$label: contrast ${"%.2f".format(ratio)}:1 is below $minimum:1")
        }
    }

    @Test
    fun `every tile's text meets AA in the default pack`() {
        forEachTile(ThemePack.CLAY_HOUSE) { role, dark, colours ->
            val where = "${role.name} ${if (dark) "dark" else "light"}"

            // Body copy, labels, metrics and affordances are normal-size text.
            assertAA("$where onSurface", colours.onSurface, colours.surface, Contrast.AA_NORMAL)
            assertAA("$where muted", colours.onSurfaceMuted, colours.surface, Contrast.AA_NORMAL)
            assertAA("$where label", colours.label, colours.surface, Contrast.AA_NORMAL)
            assertAA("$where callToAction", colours.callToAction, colours.surface, Contrast.AA_NORMAL)

            // Pill and chip copy sits on its own light background.
            assertAA("$where pill", colours.pillText, colours.pillBackground, Contrast.AA_NORMAL)
        }
    }

    @Test
    fun `meaningful non-text marks meet the 3 to 1 requirement`() {
        forEachTile(ThemePack.CLAY_HOUSE) { role, dark, colours ->
            val where = "${role.name} ${if (dark) "dark" else "light"}"
            // Ring tracks, unfilled dots and bar backgrounds all carry meaning:
            // they show the part that is not filled, so they take the full
            // non-text AA bar.
            assertAA("$where trackTint", colours.trackTint, colours.surface, Contrast.AA_NON_TEXT)

            // The field circle is decorative, and the sheet deliberately makes
            // some of them tonal — weather's circle is the sky, not a control.
            // It only has to stay perceptible.
            assertAA("$where circle", colours.circle, colours.surface, PERCEPTIBLE)
        }
    }

    @Test
    fun `every alternate theme pack is readable too`() {
        ThemePack.entries.forEach { pack ->
            forEachTile(pack) { role, dark, colours ->
                val where = "${pack.name} ${role.name} ${if (dark) "dark" else "light"}"
                assertAA("$where onSurface", colours.onSurface, colours.surface, Contrast.AA_NORMAL)
                assertAA("$where muted", colours.onSurfaceMuted, colours.surface, Contrast.AA_NORMAL)
                assertAA("$where label", colours.label, colours.surface, Contrast.AA_NORMAL)
                assertAA("$where callToAction", colours.callToAction, colours.surface, Contrast.AA_NORMAL)
            }
        }
    }

    @Test
    fun `app chrome text meets AA in both modes`() {
        listOf(false, true).forEach { dark ->
            val wallpaper = if (dark) SoftDreadPalette.WallpaperDark else SoftDreadPalette.WallpaperLight
            val surface = if (dark) SoftDreadPalette.CreamDark else SoftDreadPalette.Cream
            val onSurface = if (dark) SoftDreadPalette.TypeDark else SoftDreadPalette.Ink
            val secondary = if (dark) SoftDreadPalette.SecondaryTypeDark else SoftDreadPalette.SecondaryType
            val sectionLabel = if (dark) SoftDreadPalette.LabelOnCreamDark else SoftDreadPalette.LabelOnCream
            val mode = if (dark) "dark" else "light"

            assertAA("chrome $mode body on wallpaper", onSurface, wallpaper, Contrast.AA_NORMAL)
            assertAA("chrome $mode body on card", onSurface, surface, Contrast.AA_NORMAL)
            assertAA("chrome $mode secondary on wallpaper", secondary, wallpaper, Contrast.AA_NORMAL)
            assertAA("chrome $mode secondary on card", secondary, surface, Contrast.AA_NORMAL)
            assertAA("chrome $mode eyebrow on wallpaper", sectionLabel, wallpaper, Contrast.AA_NORMAL)
            assertAA("chrome $mode eyebrow on card", sectionLabel, surface, Contrast.AA_NORMAL)
        }
    }

    @Test
    fun `the muting solver never returns something unreadable`() {
        // Across the whole luminance range, a solved muted tone must clear AA.
        val surfaces = (0..20).map { Color(it / 20f, it / 20f, it / 20f) }
        surfaces.forEach { surface ->
            val on = Contrast.bestOn(surface, SoftDreadPalette.TypeOnColour, SoftDreadPalette.Ink)
            val muted = Contrast.muted(on, surface, Contrast.AA_NORMAL)
            // Only assert where the base colour itself could meet AA; a mid-grey
            // surface is unreachable for both, which the palette avoids.
            if (Contrast.ratio(on, surface) >= Contrast.AA_NORMAL) {
                assertAA("muted on ${surface.value}", muted, surface, Contrast.AA_NORMAL)
            }
        }
    }

    @Test
    fun `muting actually mutes where there is headroom`() {
        // Ink has huge headroom, so its secondary tone should be visibly softer
        // than its primary — hierarchy is preserved, not flattened.
        val ink = SoftDreadTiles.colours(ColourRole.INK)
        assertThat(Contrast.ratio(ink.onSurfaceMuted, ink.surface))
            .isLessThan(Contrast.ratio(ink.onSurface, ink.surface))
    }

    @Test
    fun `contrast ratio maths matches known WCAG values`() {
        val white = Color(0xFFFFFFFF)
        val black = Color(0xFF000000)
        assertThat(Contrast.ratio(white, black)).isWithin(0.01).of(21.0)
        assertThat(Contrast.ratio(white, white)).isWithin(0.01).of(1.0)
        // A well-known reference pair: #767676 on white is the AA threshold.
        assertThat(Contrast.ratio(Color(0xFF767676), white)).isWithin(0.05).of(4.54)
    }

    @Test
    fun `compositing is symmetric with a plain blend`() {
        val result = Contrast.composite(Color(0xFF000000), Color(0xFFFFFFFF), 0.5f)
        assertThat(result.red).isWithin(0.01f).of(0.5f)
        assertThat(result.alpha).isEqualTo(1f)
    }

    private fun forEachTile(pack: ThemePack, block: (ColourRole, Boolean, TileColours) -> Unit) {
        ColourRole.entries.forEach { role ->
            listOf(false, true).forEach { dark ->
                block(role, dark, SoftDreadTiles.colours(role, pack, dark))
            }
        }
    }
}
