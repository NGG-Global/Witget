package com.softdread.widgets.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing and shape tokens from the design sheet's "shape, circle rule & spacing"
 * panel. The sheet is explicit that there are no shadows and no gradients, and
 * that strokes appear only as a 1px inset on cream tiles.
 */
object SoftDreadShape {
    /** RADIUS ....... 34PX ALL TILES (28PX AT 2x1). */
    val TileRadius: Dp = 34.dp
    val TileRadiusShort: Dp = 28.dp

    /** TILE GAP ..... 10PX. */
    val TileGap: Dp = 10.dp

    /** Pill radius read off the 4x4 copy pills. */
    val PillRadius: Dp = 16.dp
    val ChipRadius: Dp = 20.dp
    val CardRadius: Dp = 18.dp

    /** CIRCLE 0 ..... 0.55-1.7x TILE WIDTH, ALWAYS CROPPED. */
    const val CIRCLE_MIN_RATIO = 0.55f
    const val CIRCLE_MAX_RATIO = 1.7f

    /** SATELLITE .... MAX ONE, 0 <= 0.4x TILE WIDTH. */
    const val SATELLITE_MAX_RATIO = 0.4f
}

/** The sheet's 4px-derived spacing rhythm, used for app chrome. */
object SoftDreadSpacing {
    val XSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val Medium: Dp = 12.dp
    val Large: Dp = 16.dp
    val XLarge: Dp = 22.dp
    val XXLarge: Dp = 34.dp
}

/** App-chrome colours, distinct from tile colours. */
data class SoftDreadChrome(
    val wallpaper: androidx.compose.ui.graphics.Color,
    val surface: androidx.compose.ui.graphics.Color,
    val onSurface: androidx.compose.ui.graphics.Color,
    val secondaryType: androidx.compose.ui.graphics.Color,
    val sectionLabel: androidx.compose.ui.graphics.Color,
    val hairline: androidx.compose.ui.graphics.Color,
    val isDark: Boolean,
    val pack: ThemePack,
)

val LocalChrome: ProvidableCompositionLocal<SoftDreadChrome> = staticCompositionLocalOf {
    error("SoftDreadTheme not applied")
}

private fun chromeFor(dark: Boolean, pack: ThemePack) = if (dark) {
    SoftDreadChrome(
        wallpaper = SoftDreadPalette.WallpaperDark,
        surface = SoftDreadPalette.CreamDark,
        onSurface = SoftDreadPalette.TypeDark,
        secondaryType = SoftDreadPalette.SecondaryTypeDark,
        sectionLabel = SoftDreadPalette.LabelOnCreamDark,
        hairline = SoftDreadPalette.TypeDark.copy(alpha = 0.18f),
        isDark = true,
        pack = pack,
    )
} else {
    SoftDreadChrome(
        wallpaper = SoftDreadPalette.WallpaperLight,
        surface = SoftDreadPalette.Cream,
        onSurface = SoftDreadPalette.Ink,
        secondaryType = SoftDreadPalette.SecondaryType,
        sectionLabel = SoftDreadPalette.LabelOnCream,
        hairline = SoftDreadPalette.Ink.copy(alpha = 0.2f),
        isDark = false,
        pack = pack,
    )
}

private val SoftDreadShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(SoftDreadShape.PillRadius),
    large = RoundedCornerShape(SoftDreadShape.CardRadius),
    extraLarge = RoundedCornerShape(SoftDreadShape.TileRadius),
)

/**
 * The app theme.
 *
 * Material 3 supplies component behaviour, but the sheet's development note is
 * explicit that "MATERIAL YOU: OPT-IN OVERRIDE, NOT THE DEFAULT — THE PACK KEEPS
 * ITS OWN COLOUR SET". So the colour scheme here is derived from the Soft Dread
 * palette rather than from a dynamic wallpaper source; Material You is offered
 * only as an explicit user choice, and only for widget tiles.
 */
@Composable
fun SoftDreadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pack: ThemePack = ThemePack.DEFAULT,
    content: @Composable () -> Unit,
) {
    val chrome = chromeFor(darkTheme, pack)
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = SoftDreadPalette.ClayDark,
            onPrimary = SoftDreadPalette.TypeDark,
            primaryContainer = SoftDreadPalette.ClayDark,
            onPrimaryContainer = SoftDreadPalette.TypeDark,
            secondary = SoftDreadPalette.SageDark,
            onSecondary = SoftDreadPalette.TypeDark,
            tertiary = SoftDreadPalette.AmberDark,
            onTertiary = SoftDreadPalette.TypeDark,
            background = chrome.wallpaper,
            onBackground = chrome.onSurface,
            surface = chrome.surface,
            onSurface = chrome.onSurface,
            surfaceVariant = SoftDreadPalette.CreamDark,
            onSurfaceVariant = chrome.secondaryType,
            outline = chrome.hairline,
            outlineVariant = chrome.hairline,
            error = SoftDreadPalette.Clay,
            onError = SoftDreadPalette.TypeOnColour,
        )
    } else {
        lightColorScheme(
            primary = SoftDreadPalette.Clay,
            onPrimary = SoftDreadPalette.TypeOnColour,
            primaryContainer = SoftDreadPalette.Cream,
            onPrimaryContainer = SoftDreadPalette.TypeInsidePill,
            secondary = SoftDreadPalette.Sage,
            onSecondary = SoftDreadPalette.TypeOnColour,
            tertiary = SoftDreadPalette.Amber,
            onTertiary = SoftDreadPalette.LabelOnAmber,
            background = chrome.wallpaper,
            onBackground = chrome.onSurface,
            surface = chrome.surface,
            onSurface = chrome.onSurface,
            surfaceVariant = SoftDreadPalette.Cream,
            onSurfaceVariant = chrome.secondaryType,
            outline = chrome.hairline,
            outlineVariant = chrome.hairline,
            error = SoftDreadPalette.Clay,
            onError = SoftDreadPalette.TypeOnColour,
        )
    }

    CompositionLocalProvider(LocalChrome provides chrome) {
        MaterialTheme(
            colorScheme = scheme,
            typography = SoftDreadTypography,
            shapes = SoftDreadShapes,
            content = content,
        )
    }
}

object SoftDreadTheme {
    val chrome: SoftDreadChrome
        @Composable @ReadOnlyComposable get() = LocalChrome.current
}
