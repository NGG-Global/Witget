package com.softdread.widgets.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.softdread.widgets.R

/**
 * The type system from the design sheet's "type scale · bricolage grotesque".
 *
 * Bricolage Grotesque ships as a variable font; each weight below is an instance
 * of the same file via [FontVariation], which keeps the APK to one 400 KB face
 * instead of four static cuts. IBM Plex Mono carries micro-labels and the
 * numeric specimen text, matching the sheet.
 */
@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: FontWeight) = Font(
    resId = R.font.bricolage_grotesque,
    weight = weight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(weight, FontStyle.Normal),
)

val BricolageGrotesque = FontFamily(
    bricolage(FontWeight.W400),
    bricolage(FontWeight.W500),
    bricolage(FontWeight.W600),
    bricolage(FontWeight.W700),
    bricolage(FontWeight.W800),
)

@OptIn(ExperimentalTextApi::class)
private fun baloo(weight: FontWeight) = Font(
    resId = R.font.baloo2,
    weight = weight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(weight, FontStyle.Normal),
)

@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: FontWeight) = Font(
    resId = R.font.nunito,
    weight = weight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(weight, FontStyle.Normal),
)

/** The wordmark's display face: chunky, rounded, friendly. Tiles never use it. */
val Baloo = FontFamily(
    baloo(FontWeight.W400),
    baloo(FontWeight.W500),
    baloo(FontWeight.W600),
    baloo(FontWeight.W700),
    baloo(FontWeight.W800),
)

/** The chrome's reading face: rounded like the mark, built for body sizes. */
val Nunito = FontFamily(
    nunito(FontWeight.W400),
    nunito(FontWeight.W500),
    nunito(FontWeight.W600),
    nunito(FontWeight.W700),
    nunito(FontWeight.W800),
)

val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.W400),
    Font(R.font.ibm_plex_mono_medium, FontWeight.W500),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.W600),
)

private val TrimBoth = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * Named styles transcribed 1:1 from the design sheet's type specimen. Line
 * heights are expressed as the sheet's unitless multipliers converted to sp, and
 * letter-spacing keeps the sheet's em values.
 */
object SoftDreadType {
    /** HERO 4x4 — 800 · 116/.84 · -.05em. */
    val Hero4x4 = TextStyle(
        fontFamily = BricolageGrotesque,
        fontWeight = FontWeight.W800,
        fontSize = 116.sp,
        lineHeight = 97.sp,
        letterSpacing = (-0.05).em,
        lineHeightStyle = TrimBoth,
    )

    /** HERO 4x2 — 800 · 46/.9 · -.03em. */
    val Hero4x2 = TextStyle(
        fontFamily = BricolageGrotesque,
        fontWeight = FontWeight.W800,
        fontSize = 46.sp,
        lineHeight = 41.sp,
        letterSpacing = (-0.03).em,
        lineHeightStyle = TrimBoth,
    )

    /** HERO 2x2 — 800 · 38/.9 · -.04em. */
    val Hero2x2 = TextStyle(
        fontFamily = BricolageGrotesque,
        fontWeight = FontWeight.W800,
        fontSize = 38.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.04).em,
        lineHeightStyle = TrimBoth,
    )

    /** SUBHEAD 4x4 — 600 · 23/1.1. */
    val Subhead = TextStyle(
        fontFamily = BricolageGrotesque,
        fontWeight = FontWeight.W600,
        fontSize = 23.sp,
        lineHeight = 25.sp,
        lineHeightStyle = TrimBoth,
    )

    /** VOICE 4x2 — 600 · 16/1.2. */
    val Voice = TextStyle(
        fontFamily = BricolageGrotesque,
        fontWeight = FontWeight.W600,
        fontSize = 16.sp,
        lineHeight = 19.sp,
        lineHeightStyle = TrimBoth,
    )

    /** VOICE 2x2 · MIN — 500 · 13/1.22. The sheet's floor: never below 13/500. */
    val VoiceCompact = TextStyle(
        fontFamily = BricolageGrotesque,
        fontWeight = FontWeight.W500,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        lineHeightStyle = TrimBoth,
    )

    /** MICRO-LABEL — 600 · 10.5/1 · .14em · uppercase. */
    val MicroLabel = TextStyle(
        fontFamily = BricolageGrotesque,
        fontWeight = FontWeight.W600,
        fontSize = 10.5.sp,
        lineHeight = 11.sp,
        letterSpacing = 0.14.em,
        lineHeightStyle = TrimBoth,
    )

    /** The sheet's spec/annotation voice, used for app chrome metadata. */
    val Mono = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.W400,
        fontSize = 11.sp,
        lineHeight = 18.sp,
    )

    val MonoLabel = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.W600,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.06.em,
    )

    /** Section eyebrow used throughout the sheet: 600 · 11 · .16em · uppercase. */
    val SectionLabel = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.W800,
        fontSize = 11.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.16.em,
    )
}

/**
 * Material 3 typography for the app chrome: the sheet's scale, set in Space
 * Grotesk. Tile specimens keep Bricolage through [SoftDreadType]'s hero/voice
 * styles, so the two voices meet on every screen — chrome speaks Space
 * Grotesk, the widgets speak the sheet.
 */
val SoftDreadTypography = Typography(
    displayLarge = SoftDreadType.Hero4x4.copy(fontSize = 64.sp, lineHeight = 60.sp, fontFamily = Nunito),
    displayMedium = SoftDreadType.Hero4x2.copy(fontFamily = Nunito),
    displaySmall = SoftDreadType.Hero2x2.copy(fontFamily = Nunito),
    headlineLarge = TextStyle(
        fontFamily = Baloo,
        fontWeight = FontWeight.W800,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.03).em,
    ),
    headlineMedium = TextStyle(
        fontFamily = Baloo,
        fontWeight = FontWeight.W600,
        fontSize = 25.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.01).em,
    ),
    headlineSmall = SoftDreadType.Subhead.copy(fontFamily = Nunito),
    titleLarge = TextStyle(
        fontFamily = Baloo,
        fontWeight = FontWeight.W600,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.01).em,
    ),
    titleMedium = TextStyle(
        fontFamily = Baloo,
        fontWeight = FontWeight.W600,
        fontSize = 17.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.01).em,
    ),
    titleSmall = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.W400,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.W400,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.W400,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = SoftDreadType.SectionLabel,
    labelSmall = SoftDreadType.MicroLabel.copy(fontFamily = Nunito),
)
