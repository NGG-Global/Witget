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
    //
    // These are the sheet's exact values. Four of them (clay, ember, sage,
    // slate) cannot carry readable body copy in either type colour as-is, so
    // SoftDreadTiles.colours() corrects their lightness by a few percent at
    // resolve time via Contrast.legibleField. Keeping the literals honest here
    // means the palette in source still matches the design sheet, and the same
    // correction protects the alternate theme packs for free.
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
    /**
     * Light wallpaper / app canvas: the Witget logo's own cream. Warmer and
     * lighter than the sheet's #E7DED3, and measured — every chrome text tone
     * gains contrast on it (ink 11.1:1, secondary 5.4:1, accent 8.8:1).
     */
    val WallpaperLight = Color(0xFFFCEEDC)
    /** Micro-label on a cream surface. */
    /** Sheet #A2664A, darkened so the eyebrow label clears AA on cream. */
    val LabelOnCream = Color(0xFF87553E)
    /** Micro-label on the amber tile (the only tile with ink type on colour). */
    val LabelOnAmber = Color(0xFF7A5410)
    /** Secondary type throughout the sheet. */
    /** Sheet #8A7666, darkened so app body copy clears AA on cream and wallpaper. */
    val SecondaryType = Color(0xFF6E5E51)
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

    /**
     * The logo's coral, used for decorative brand marks only — the i-dot motif:
     * page indicators, nav dots, the wordmark's full stop. Never text: as a
     * mark it clears the 3:1 non-text bar (3.5-3.7:1 light, 3.9-4.7:1 dark),
     * which clay-as-text never could.
     */
    val BrandDotLight = Color(0xFFC65B3C)
    val BrandDotDark = Color(0xFFCC6848)
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
    /** Secondary affordance text ("tap to ask again"), still AA on the field. */
    val callToAction: Color,
    /** Ring tracks, unfilled dots and bar backgrounds: AA for non-text marks. */
    val trackTint: Color,
    /** The 1px inset edge on tiles that need one. */
    val hairline: Color,
    val satellite: Color,
    /**
     * A circle colour that still reads on this field. Cream tiles get a slate
     * disc, because a cream circle on cream would disappear.
     */
    val contrastCircle: Color,
    val pillBackground: Color,
    val pillText: Color,
    /** The emphasised metadata chip (joke's "regret level"): resolved clay. */
    val chipEmphasisBg: Color,
    val chipEmphasisOn: Color,
    val needsInsetStroke: Boolean,
)

object SoftDreadTiles {

    /**
     * The permanent tile colour for [role]. The design sheet's theme packs swap
     * the five field colours; ink and cream keep their structural roles so the
     * dark tile stays the anchor and the light tile stays the one light tile.
     */
    /** The design sheet's own swatch, before any legibility correction. */
    fun sheetField(role: ColourRole, pack: ThemePack, dark: Boolean): Color = field(role, pack, dark)

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
        // The sheet's swatch, corrected in lightness only if no type colour
        // could otherwise reach AA on it.
        val surface = Contrast.legibleField(
            surface = field(role, pack, dark),
            light = if (dark) SoftDreadPalette.TypeDark else SoftDreadPalette.TypeOnColour,
            dark = SoftDreadPalette.Ink,
        )

        // The sheet names amber as "the only tile with ink type on colour". That
        // is a measurement, not a preference, so it is measured here rather than
        // listed: whichever of the two type colours reads better on this field
        // wins. Light fields therefore take ink and dark fields take cream, in
        // every theme pack, without anyone maintaining a table.
        val onSurface = Contrast.bestOn(
            surface,
            if (dark) SoftDreadPalette.TypeDark else SoftDreadPalette.TypeOnColour,
            SoftDreadPalette.Ink,
        )

        // Secondary tones are solved for, not assigned an opacity: a tile with
        // headroom gets a genuinely soft tone, a tight one barely mutes at all.
        val muted = Contrast.muted(onSurface, surface, Contrast.AA_NORMAL)
        val label = labelFor(role, surface, onSurface, dark)
        // The emphasised chip borrows another resolved tile — clay normally,
        // night on the clay tile itself — so its copy goes through the same
        // legibility correction as everything else.
        val emphasis = resolveField(
            if (role == ColourRole.CLAY) ColourRole.NIGHT else ColourRole.CLAY,
            pack,
            dark,
        )
        val circleFill = if (dark) SoftDreadPalette.CircleFillDark else SoftDreadPalette.Cream

        return TileColours(
            surface = surface,
            onSurface = onSurface,
            onSurfaceMuted = muted,
            label = label,
            // A cream circle on the cream tile would be invisible, so the field
            // art falls back to a contrasting disc when its own fill vanishes.
            circle = if (role == ColourRole.INK) {
                Contrast.tint(onSurface, surface)
            } else {
                Contrast.perceptibleShape(circleFill, surface, field(ColourRole.SLATE, pack, dark))
            },
            circleTint = Contrast.tint(onSurface, surface),
            callToAction = muted,
            trackTint = Contrast.tint(onSurface, surface),
            hairline = Contrast.tint(onSurface, surface, minRatio = 1.6),
            satellite = satelliteFor(role, pack, dark),
            contrastCircle = if (role == ColourRole.CREAM) field(ColourRole.SLATE, pack, dark) else circleFill,
            pillBackground = if (role == ColourRole.INK) circleFill else SoftDreadPalette.TypeOnColour,
            chipEmphasisBg = emphasis.surface,
            chipEmphasisOn = emphasis.onSurface,
            pillText = pillTextFor(role, surface, if (role == ColourRole.INK) circleFill else SoftDreadPalette.TypeOnColour),
            needsInsetStroke = role == ColourRole.CREAM || (dark && role == ColourRole.INK),
        )
    }

    /** A field with the type colour that reads best on it, both solved. */
    private data class ResolvedField(val surface: Color, val onSurface: Color)

    private fun resolveField(role: ColourRole, pack: ThemePack, dark: Boolean): ResolvedField {
        val surface = Contrast.legibleField(
            surface = field(role, pack, dark),
            light = if (dark) SoftDreadPalette.TypeDark else SoftDreadPalette.TypeOnColour,
            dark = SoftDreadPalette.Ink,
        )
        return ResolvedField(
            surface = surface,
            onSurface = Contrast.bestOn(
                surface,
                if (dark) SoftDreadPalette.TypeDark else SoftDreadPalette.TypeOnColour,
                SoftDreadPalette.Ink,
            ),
        )
    }

    /**
     * The micro-label tone. The sheet gives a specific label colour for the
     * cream and amber tiles; those are honoured wherever they still clear AA on
     * the resolved field, and otherwise the label falls back to a solved muted
     * tone rather than being left unreadable.
     */
    private fun labelFor(role: ColourRole, surface: Color, onSurface: Color, dark: Boolean): Color {
        val preferred = when {
            dark && role == ColourRole.CREAM -> SoftDreadPalette.LabelOnCreamDark
            dark -> null
            role == ColourRole.AMBER -> SoftDreadPalette.LabelOnAmber
            role == ColourRole.CREAM -> SoftDreadPalette.LabelOnCream
            else -> null
        }
        if (preferred != null && Contrast.meets(preferred, surface, Contrast.AA_NORMAL)) return preferred
        return Contrast.muted(onSurface, surface, Contrast.AA_NORMAL)
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

    /**
     * The sheet gives each 4x4 pill its own dark type colour. Each is kept where
     * it clears AA on the pill's own background, and darkened until it does
     * otherwise — a pill is a light chip, so its copy has to hold up on cream
     * rather than on the tile.
     */
    private fun pillTextFor(role: ColourRole, surface: Color, pillBackground: Color): Color {
        val preferred = when (role) {
            ColourRole.CLAY -> SoftDreadPalette.TypeInsidePill
            ColourRole.SAGE -> SoftDreadPalette.PillTypeOnSage
            ColourRole.EMBER -> SoftDreadPalette.PillTypeOnEmber
            ColourRole.SLATE -> SoftDreadPalette.PillTypeOnSlate
            ColourRole.INK -> SoftDreadPalette.Ink
            ColourRole.CREAM -> SoftDreadPalette.TypeOnColour
            else -> surface.darkenBy(0.45f)
        }
        if (Contrast.meets(preferred, pillBackground, Contrast.AA_NORMAL)) return preferred
        var candidate = preferred
        repeat(20) {
            if (Contrast.meets(candidate, pillBackground, Contrast.AA_NORMAL)) return candidate
            candidate = candidate.darkenBy(0.12f)
        }
        return SoftDreadPalette.Ink
    }
}
