package com.softdread.widgets.design

import androidx.compose.ui.graphics.Color
import com.softdread.widgets.domain.model.ColourRole

/**
 * Colour tokens transcribed from `docs/references/Soft_Dread_Design_Sheet.html`.
 *
 * The design sheet's first rule is "one colour per widget; it never changes
 * across sizes, states or modes", and its identity note explains the whole pack
 * re-skins by swapping seven hex values. [ThemePack] is that swap point — every
 * surface in the app and every tile reads its colour through a pack rather than
 * through a hard-coded hex.
 */
object SoftDreadPalette {

    // --- light mode · "palette · one owner per swatch" -----------------------
    val Clay = Color(0xFFC65B3C)
    val Ember = Color(0xFFE0762E)
    val Amber = Color(0xFFF0B23F)
    val Sage = Color(0xFF7F9C7A)
    val Slate = Color(0xFF5F7FA8)
    val Night = Color(0xFF34435C)
    val Ink = Color(0xFF3B3128)
    val Cream = Color(0xFFF2E7D8)

    /** Type on a colour field. */
    val TypeOnColour = Color(0xFFFFF6EC)
    /** Light wallpaper / app canvas. */
    val WallpaperLight = Color(0xFFE7DED3)
    /** Micro-label on a cream surface. */
    val LabelOnCream = Color(0xFFA2664A)
    /** Micro-label on the amber tile (the only tile with ink type on colour). */
    val LabelOnAmber = Color(0xFF7A5410)
    /** Secondary type throughout the sheet. */
    val SecondaryType = Color(0xFF8A7666)
    /** Type inside a cream pill on a clay tile. */
    val TypeInsidePill = Color(0xFF7A2F1C)

    // Pill type colours read off the individual 4x4 tiles in the widget matrix.
    val PillTypeOnSage = Color(0xFF3D4F3A)
    val PillTypeOnEmber = Color(0xFF8A3D0E)
    val PillTypeOnSlate = Color(0xFF2F4A68)

    // --- dark mode · "same hue, lower lightness" ----------------------------
    val ClayDark = Color(0xFFA34630)
    val EmberDark = Color(0xFF8F4A1C)
    val AmberDark = Color(0xFF96701F)
    val SageDark = Color(0xFF4E6349)
    val SlateDark = Color(0xFF3F5674)
    val NightDark = Color(0xFF232D3D)
    val InkDark = Color(0xFF14110E)
    /** The cream tile inverts rather than darkening. */
    val CreamDark = Color(0xFF302921)

    val TypeDark = Color(0xFFF2E7D8)
    val CircleFillDark = Color(0xFFE2D3C1)
    val WallpaperDark = Color(0xFF1C1814)
    val LabelOnCreamDark = Color(0xFFC08D6B)
    val SecondaryTypeDark = Color(0xFFA89579)
}

/**
 * A named colour set. The design sheet lists four ("future theme packs · swap 7
 * hex values"); V1 ships the default and the three alternates as selectable
 * accents for the app chrome, with `CLAY_HOUSE` as the widget default.
 */
enum class ThemePack(val displayKey: String) {
    CLAY_HOUSE("clay_house"),
    DUSK_HOUSE("dusk_house"),
    ORCHARD_HOUSE("orchard_house"),
    PAPER_HOUSE("paper_house"),
    ;

    companion object {
        val DEFAULT = CLAY_HOUSE
        fun fromKeyOrDefault(key: String?): ThemePack =
            entries.firstOrNull { it.displayKey == key } ?: DEFAULT
    }
}

/** Resolved colour set for one [ThemePack] in one light/dark mode. */
data class TileColours(
    val surface: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val label: Color,
    val circle: Color,
    val circleTint: Color,
    val satellite: Color,
    /**
     * A circle colour that still reads on this field. Cream tiles get a slate
     * disc, because a cream circle on cream would disappear.
     */
    val contrastCircle: Color,
    val pillBackground: Color,
    val pillText: Color,
    val needsInsetStroke: Boolean,
)

object SoftDreadTiles {

    /**
     * The permanent tile colour for [role]. The design sheet's theme packs swap
     * the five field colours; ink and cream keep their structural roles so the
     * dark tile stays the anchor and the light tile stays the one light tile.
     */
    private fun field(role: ColourRole, pack: ThemePack, dark: Boolean): Color = when (pack) {
        ThemePack.CLAY_HOUSE -> if (dark) darkDefault(role) else lightDefault(role)
        ThemePack.DUSK_HOUSE -> shiftPack(
            role, dark,
            Color(0xFF7D5A8C), Color(0xFFA56B93), Color(0xFFE0A6A0), Color(0xFF6F8BA0), Color(0xFF43506B),
        )
        ThemePack.ORCHARD_HOUSE -> shiftPack(
            role, dark,
            Color(0xFF2F6F5E), Color(0xFF5A9C74), Color(0xFFD4C25A), Color(0xFF8AA87C), Color(0xFF274B47),
        )
        ThemePack.PAPER_HOUSE -> shiftPack(
            role, dark,
            Color(0xFF3B3128), Color(0xFF6B5C4C), Color(0xFFA89579), Color(0xFFCDBCA4), Color(0xFFE7DED3),
        )
    }

    private fun lightDefault(role: ColourRole): Color = when (role) {
        ColourRole.CLAY -> SoftDreadPalette.Clay
        ColourRole.EMBER -> SoftDreadPalette.Ember
        ColourRole.AMBER -> SoftDreadPalette.Amber
        ColourRole.SAGE -> SoftDreadPalette.Sage
        ColourRole.SLATE -> SoftDreadPalette.Slate
        ColourRole.NIGHT -> SoftDreadPalette.Night
        ColourRole.INK -> SoftDreadPalette.Ink
        ColourRole.CREAM -> SoftDreadPalette.Cream
    }

    private fun darkDefault(role: ColourRole): Color = when (role) {
        ColourRole.CLAY -> SoftDreadPalette.ClayDark
        ColourRole.EMBER -> SoftDreadPalette.EmberDark
        ColourRole.AMBER -> SoftDreadPalette.AmberDark
        ColourRole.SAGE -> SoftDreadPalette.SageDark
        ColourRole.SLATE -> SoftDreadPalette.SlateDark
        ColourRole.NIGHT -> SoftDreadPalette.NightDark
        ColourRole.INK -> SoftDreadPalette.InkDark
        ColourRole.CREAM -> SoftDreadPalette.CreamDark
    }

    /**
     * Applies a theme pack's five swatches to the five "field" roles, darkening
     * them for dark mode by the sheet's rule (tiles drop 22-26% lightness).
     */
    private fun shiftPack(
        role: ColourRole,
        dark: Boolean,
        first: Color,
        second: Color,
        third: Color,
        fourth: Color,
        fifth: Color,
    ): Color {
        val base = when (role) {
            ColourRole.CLAY -> first
            ColourRole.EMBER -> second
            ColourRole.AMBER -> third
            ColourRole.SAGE -> fourth
            ColourRole.SLATE -> fifth
            ColourRole.NIGHT -> if (dark) SoftDreadPalette.NightDark else SoftDreadPalette.Night
            ColourRole.INK -> if (dark) SoftDreadPalette.InkDark else SoftDreadPalette.Ink
            ColourRole.CREAM -> if (dark) SoftDreadPalette.CreamDark else SoftDreadPalette.Cream
        }
        val structural = role == ColourRole.NIGHT || role == ColourRole.INK || role == ColourRole.CREAM
        return if (dark && !structural) base.darkenBy(0.24f) else base
    }

    private fun Color.darkenBy(fraction: Float): Color =
        Color(red * (1f - fraction), green * (1f - fraction), blue * (1f - fraction), alpha)

    /**
     * Resolves the full colour set for a tile.
     *
     * Two roles read type differently, exactly as the sheet specifies: amber is
     * "the only tile with ink type on colour", and cream is "the one light tile"
     * which carries a 1px inset stroke so it holds an edge on pale wallpapers.
     */
    fun colours(role: ColourRole, pack: ThemePack = ThemePack.DEFAULT, dark: Boolean = false): TileColours {
        val surface = field(role, pack, dark)
        val inkOnColour = role == ColourRole.AMBER || role == ColourRole.CREAM
        val onSurface = when {
            dark -> SoftDreadPalette.TypeDark
            inkOnColour -> SoftDreadPalette.Ink
            else -> SoftDreadPalette.TypeOnColour
        }
        val label = when {
            dark && role == ColourRole.CREAM -> SoftDreadPalette.LabelOnCreamDark
            dark -> SoftDreadPalette.TypeDark.copy(alpha = 0.78f)
            role == ColourRole.AMBER -> SoftDreadPalette.LabelOnAmber
            role == ColourRole.CREAM -> SoftDreadPalette.LabelOnCream
            role == ColourRole.INK -> SoftDreadPalette.Cream.copy(alpha = 0.70f)
            else -> SoftDreadPalette.TypeOnColour.copy(alpha = 0.82f)
        }
        val circleFill = if (dark) SoftDreadPalette.CircleFillDark else SoftDreadPalette.Cream
        return TileColours(
            surface = surface,
            onSurface = onSurface,
            onSurfaceMuted = onSurface.copy(alpha = if (dark) 0.72f else 0.78f),
            label = label,
            circle = if (role == ColourRole.INK) onSurface.copy(alpha = 0.09f) else circleFill,
            circleTint = onSurface.copy(alpha = if (dark) 0.14f else 0.16f),
            satellite = satelliteFor(role, pack, dark),
            contrastCircle = if (role == ColourRole.CREAM) field(ColourRole.SLATE, pack, dark) else circleFill,
            pillBackground = if (role == ColourRole.INK) circleFill else SoftDreadPalette.TypeOnColour,
            pillText = pillTextFor(role, surface),
            needsInsetStroke = role == ColourRole.CREAM || (dark && role == ColourRole.INK),
        )
    }

    /**
     * The optional single satellite circle. The sheet pairs specific tiles:
     * screen time gets a sage dot, joke gets ember, day vibe gets amber,
     * countdown gets amber. Weather's satellite is the sky itself and is chosen
     * by condition instead, so it is overridden at the call site.
     */
    private fun satelliteFor(role: ColourRole, pack: ThemePack, dark: Boolean): Color = when (role) {
        ColourRole.CLAY -> field(ColourRole.SAGE, pack, dark)
        ColourRole.AMBER -> field(ColourRole.EMBER, pack, dark)
        ColourRole.CREAM -> field(ColourRole.AMBER, pack, dark)
        ColourRole.SLATE -> field(ColourRole.AMBER, pack, dark)
        else -> field(ColourRole.AMBER, pack, dark)
    }

    private fun pillTextFor(role: ColourRole, surface: Color): Color = when (role) {
        ColourRole.CLAY -> SoftDreadPalette.TypeInsidePill
        ColourRole.SAGE -> SoftDreadPalette.PillTypeOnSage
        ColourRole.EMBER -> SoftDreadPalette.PillTypeOnEmber
        ColourRole.SLATE -> SoftDreadPalette.PillTypeOnSlate
        ColourRole.INK -> SoftDreadPalette.Ink
        ColourRole.CREAM -> SoftDreadPalette.TypeOnColour
        else -> surface.darkenBy(0.45f)
    }
}
