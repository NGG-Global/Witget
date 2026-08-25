package com.softdread.widgets.design

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG contrast maths, used to derive readable text colours instead of guessing
 * at opacity values.
 *
 * The pack's tiles are flat mid-tone fields, and a muted colour that looks
 * comfortable on ink is unreadable on ember. Rather than hand-tuning an alpha
 * per tile — which is what made the first pass fail — every muted, label and
 * secondary colour here is *solved for*: the tone is pushed toward the field
 * only as far as the required ratio allows. That also means the alternate theme
 * packs, whose colours nobody hand-checked, stay readable automatically.
 */
object Contrast {

    /** WCAG AA for normal text. Applies to body copy, labels and metrics. */
    const val AA_NORMAL = 4.5

    /** WCAG AA for large text: >= 24sp, or >= 19sp when semi-bold or heavier. */
    const val AA_LARGE = 3.0

    /** WCAG AA for meaningful non-text elements: rings, bars, dots, strokes. */
    const val AA_NON_TEXT = 3.0

    /** Relative luminance per WCAG 2.x. */
    fun luminance(colour: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(colour.red) +
            0.7152 * channel(colour.green) +
            0.0722 * channel(colour.blue)
    }

    /** Contrast ratio between two opaque colours, from 1.0 to 21.0. */
    fun ratio(foreground: Color, background: Color): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    /** Composites [foreground] at [alpha] over an opaque [background]. */
    fun composite(foreground: Color, background: Color, alpha: Float): Color = Color(
        red = foreground.red * alpha + background.red * (1f - alpha),
        green = foreground.green * alpha + background.green * (1f - alpha),
        blue = foreground.blue * alpha + background.blue * (1f - alpha),
        alpha = 1f,
    )

    /**
     * Whichever of the two type colours reads better on [surface].
     *
     * (Taken as two parameters rather than a vararg: [Color] is an inline value
     * class, which Kotlin will not accept as a vararg element type.)
     */
    fun bestOn(surface: Color, first: Color, second: Color): Color =
        if (ratio(first, surface) >= ratio(second, surface)) first else second

    /**
     * The most muted version of [onSurface] that still clears [minRatio] against
     * [surface].
     *
     * A tile with plenty of headroom — ink, night — gets a genuinely soft
     * secondary tone; a tight one such as ember barely mutes at all, which is
     * the correct trade: hierarchy there comes from size and weight instead.
     */
    fun muted(
        onSurface: Color,
        surface: Color,
        minRatio: Double = AA_NORMAL,
        floor: Float = 0.55f,
    ): Color {
        if (ratio(onSurface, surface) < minRatio) return onSurface
        // Binary search the smallest alpha meeting the ratio; 12 iterations
        // resolve to well under one 8-bit step.
        var low = floor
        var high = 1f
        repeat(12) {
            val mid = (low + high) / 2f
            if (ratio(composite(onSurface, surface, mid), surface) >= minRatio) {
                high = mid
            } else {
                low = mid
            }
        }
        return composite(onSurface, surface, high)
    }

    /**
     * A tint of [onSurface] for non-text marks that still carry meaning — a ring
     * track, an unfilled dot, a bar's background.
     */
    fun tint(onSurface: Color, surface: Color, minRatio: Double = AA_NON_TEXT): Color =
        muted(onSurface, surface, minRatio, floor = 0.12f)

    /**
     * Nudges a field colour's lightness until one of the two type colours can
     * clear [minRatio] on it, and returns it unchanged when one already does.
     *
     * Mid-tone fields are the hard case: at the design sheet's exact values,
     * clay, ember, sage and slate cannot carry readable body copy in either
     * type colour. Rather than hand-editing each swatch — which would leave the
     * three alternate theme packs broken in the same way — the correction is
     * computed here, so the palette in source stays the sheet's own values and
     * every pack, present or future, is legible by construction.
     *
     * Only lightness moves; hue and saturation are untouched, so the swatch is
     * still recognisably itself.
     */
    fun legibleField(
        surface: Color,
        light: Color,
        dark: Color,
        minRatio: Double = AA_NORMAL,
    ): Color {
        fun best(candidate: Color) = max(ratio(light, candidate), ratio(dark, candidate))
        if (best(surface) >= minRatio) return surface

        val hsl = surface.toHsl()
        // Search outward from the original lightness and take the first value in
        // either direction that works, so the correction is always the smallest
        // one available.
        for (step in 1..120) {
            val delta = step / 400f
            for (candidate in listOf(hsl.withLightness(hsl.lightness - delta), hsl.withLightness(hsl.lightness + delta))) {
                if (best(candidate) >= minRatio) return candidate
            }
        }
        return surface
    }

    /** A circle colour that stays visible on [surface]; used for the field art. */
    fun perceptibleShape(preferred: Color, surface: Color, fallback: Color, minRatio: Double = 1.25): Color =
        if (ratio(preferred, surface) >= minRatio) preferred else fallback

    private data class Hsl(val hue: Float, val saturation: Float, val lightness: Float)

    private fun Color.toHsl(): Hsl {
        val max = maxOf(red, green, blue)
        val min = minOf(red, green, blue)
        val lightness = (max + min) / 2f
        if (max == min) return Hsl(0f, 0f, lightness)
        val delta = max - min
        val saturation = if (lightness > 0.5f) delta / (2f - max - min) else delta / (max + min)
        val hue = when (max) {
            red -> ((green - blue) / delta + if (green < blue) 6f else 0f)
            green -> ((blue - red) / delta + 2f)
            else -> ((red - green) / delta + 4f)
        } / 6f
        return Hsl(hue, saturation, lightness)
    }

    private fun Hsl.withLightness(value: Float): Color {
        val l = value.coerceIn(0f, 1f)
        if (saturation == 0f) return Color(l, l, l)
        val q = if (l < 0.5f) l * (1f + saturation) else l + saturation - l * saturation
        val p = 2f * l - q
        fun channel(t0: Float): Float {
            var t = t0
            if (t < 0f) t += 1f
            if (t > 1f) t -= 1f
            return when {
                t < 1f / 6f -> p + (q - p) * 6f * t
                t < 1f / 2f -> q
                t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
                else -> p
            }
        }
        return Color(channel(hue + 1f / 3f), channel(hue), channel(hue - 1f / 3f))
    }

    /** True when [foreground] on [surface] meets [minRatio]. */
    fun meets(foreground: Color, surface: Color, minRatio: Double): Boolean =
        ratio(foreground, surface) >= minRatio
}
