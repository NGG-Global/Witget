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
    fun `chrome controls meet AA in both modes`() {
        // These are the pairings that failed in the second device build: raw
        // clay as an accent reads 2.8-4.0:1. The chrome accent must clear AA as
        // a fill, as text on the wallpaper and as text on a card.
        listOf(false, true).forEach { dark ->
            val chrome = chromeFixture(dark)
            val mode = if (dark) "dark" else "light"

            assertAA("$mode onAccent on accent fill", chrome.onAccent, chrome.accent, Contrast.AA_NORMAL)
            assertAA("$mode accent text on wallpaper", chrome.accentText, chrome.wallpaper, Contrast.AA_NORMAL)
            assertAA("$mode accent text on card", chrome.accentText, chrome.surface, Contrast.AA_NORMAL)

            // The brand dot is decorative but stateful (page/nav indicators),
            // so it must clear the non-text bar on both grounds.
            val brandDot = if (dark) SoftDreadPalette.BrandDotDark else SoftDreadPalette.BrandDotLight
            assertAA("$mode brand dot on wallpaper", brandDot, chrome.wallpaper, Contrast.AA_NON_TEXT)
            assertAA("$mode brand dot on card", brandDot, chrome.surface, Contrast.AA_NON_TEXT)

            // Status badges borrow the resolved sage and amber tiles.
            listOf(ColourRole.SAGE, ColourRole.AMBER).forEach { role ->
                val tile = SoftDreadTiles.colours(role, ThemePack.CLAY_HOUSE, dark)
                assertAA("$mode badge ${role.name}", tile.onSurface, tile.surface, Contrast.AA_NORMAL)
            }
        }
    }

    @Test
    fun `widget picker preview type reads on its corrected field`() {
        // Mirrors res/layout/widget_preview_*.xml + values(-night)/colors.xml.
        val light = mapOf(
            Color(0xFFB95336) to Color(0xFFFFF6EC), // screen time
            Color(0xFFF0B23F) to Color(0xFF3B3128), // daily joke
            Color(0xFF87A282) to Color(0xFF3B3128), // battery
            Color(0xFFF2E7D8) to Color(0xFF3B3128), // day vibe
            Color(0xFFE38240) to Color(0xFF3B3128), // weather
            Color(0xFF55749C) to Color(0xFFFFF6EC), // countdown
            Color(0xFF3B3128) to Color(0xFFF2E7D8), // time progress
            Color(0xFF34435C) to Color(0xFFFFF6EC), // magic 8 ball
        )
        val dark = listOf(
            Color(0xFFA34630), Color(0xFF85631C), Color(0xFF4E6349), Color(0xFF302921),
            Color(0xFF8F4A1C), Color(0xFF3F5674), Color(0xFF14110E), Color(0xFF232D3D),
        )
        light.forEach { (field, ink) ->
            assertAA("picker light on ${field.value}", ink, field, Contrast.AA_NORMAL)
        }
        dark.forEach { field ->
            assertAA("picker dark on ${field.value}", Color(0xFFF2E7D8), field, Contrast.AA_NORMAL)
        }
    }

    /**
     * Rebuilds the chrome tokens without composing. Must stay in step with
     * chromeFor() in Theme.kt; the values are simple enough that drift would be
     * caught by eye, and the alternative is composing a theme in a unit test.
     */
    private fun chromeFixture(dark: Boolean) = if (dark) {
        ChromeColours(
            wallpaper = SoftDreadPalette.WallpaperDark,
            surface = SoftDreadPalette.CreamDark,
            accent = SoftDreadPalette.CircleFillDark,
            onAccent = SoftDreadPalette.Ink,
            accentText = SoftDreadPalette.CircleFillDark,
        )
    } else {
        ChromeColours(
            wallpaper = SoftDreadPalette.WallpaperLight,
            surface = SoftDreadPalette.Cream,
            accent = SoftDreadPalette.Night,
            onAccent = SoftDreadPalette.TypeOnColour,
            accentText = SoftDreadPalette.Night,
        )
    }

    private data class ChromeColours(
        val wallpaper: Color,
        val surface: Color,
        val accent: Color,
        val onAccent: Color,
        val accentText: Color,
    )

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
