package com.softdread.widgets.domain.logic

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Number and duration formatting shared by every widget.
 *
 * Two rules from the design sheet govern this file: "NUMERALS TABULAR, ONE
 * DECIMAL MAX", and the Screen Time note "numeral takes one decimal, never two".
 * Everything is [Locale]-aware so a future localisation gets native numerals and
 * separators without touching call sites.
 */
object Formatting {

    /** "45 min", "1h 40m", "3h" — the compact duration used across the pack. */
    fun hoursShort(totalMinutes: Int, locale: Locale = Locale.getDefault()): String {
        val safe = totalMinutes.coerceAtLeast(0)
        if (safe < 60) return String.format(locale, "%d min", safe)
        val hours = safe / 60
        val minutes = safe % 60
        return if (minutes == 0) {
            String.format(locale, "%dh", hours)
        } else {
            String.format(locale, "%dh %02dm", hours, minutes)
        }
    }

    /** "2h 10m" style used for the battery's estimated-remaining metric. */
    fun hoursMinutes(totalMinutes: Int, locale: Locale = Locale.getDefault()): String =
        hoursShort(totalMinutes, locale)

    /**
     * One decimal below ten, a rounded integer at ten and above, and no trailing
     * ".0" — the Bible's Screen Time formatting rule, reused wherever a ratio or
     * a human-scale unit is displayed.
     */
    fun oneDecimal(value: Double, locale: Locale = Locale.getDefault()): String {
        if (!value.isFinite()) return "0"
        return if (abs(value) >= 10.0) {
            String.format(locale, "%d", value.roundToLong())
        } else {
            val rounded = (value * 10.0).roundToInt() / 10.0
            if (rounded == rounded.toLong().toDouble()) {
                String.format(locale, "%d", rounded.toLong())
            } else {
                String.format(locale, "%.1f", rounded)
            }
        }
    }

    fun percent(value: Double, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%d", value.roundToInt().coerceIn(0, 100))

    fun integer(value: Long, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%d", value)

    fun temperature(celsius: Double, useCelsius: Boolean, locale: Locale = Locale.getDefault()): String {
        val value = if (useCelsius) celsius else celsius * 9.0 / 5.0 + 32.0
        return String.format(locale, "%d", value.roundToInt())
    }
}
