package com.softdread.widgets.domain.logic

import com.softdread.widgets.data.content.EquivalencyUnit
import com.softdread.widgets.data.prefs.EquivalencyCategory
import kotlin.math.abs
import kotlin.random.Random

/** A chosen equivalency and how today's usage compares to it. */
data class EquivalencyMatch(
    val unit: EquivalencyUnit,
    val ratio: Double,
    /** The ratio rendered per the Bible's formatting rule. */
    val displayRatio: String,
    /**
     * True when the Bible's "show percentage when clearer" rule applies, i.e. the
     * usage is less than one whole unit.
     */
    val asPercentage: Boolean,
)

/**
 * Screen Time band selection, equivalency matching and formatting.
 *
 * The Bible keeps the real metric visible and treats the equivalency as playful
 * context, so [variables] always exposes both `{minutes}` and `{hours_short}`
 * alongside `{ratio}` and `{equivalent}`.
 */
object ScreenTimeLogic {

    /** Bands: <30, 30-89, 90-179, 180-299, 300-479, >=480 minutes. */
    fun stateKey(usageMinutes: Int): String = when {
        usageMinutes < 30 -> "ST_B1"
        usageMinutes < 90 -> "ST_B2"
        usageMinutes < 180 -> "ST_B3"
        usageMinutes < 300 -> "ST_B4"
        usageMinutes < 480 -> "ST_B5"
        else -> "ST_B6"
    }

    const val FRAMING_STATE_KEY = "ST_EQ"

    private const val PREFERRED_MIN = 0.5
    private const val PREFERRED_MAX = 12.0
    private const val WIDENED_MIN = 0.2
    private const val WIDENED_MAX = 25.0

    /**
     * Chooses an equivalency for [usageMinutes].
     *
     * The Bible prefers a ratio between 0.5 and 12.0 and widens to 0.2-25.0 when
     * nothing fits. [excludedUnitIds] carries the seven-day equivalency cooldown;
     * it is relaxed before the ratio window is, because showing a repeat unit is
     * a smaller failure than showing an absurd ratio.
     */
    fun chooseEquivalency(
        usageMinutes: Int,
        units: List<EquivalencyUnit>,
        category: EquivalencyCategory = EquivalencyCategory.MIXED,
        excludedUnitIds: Set<String> = emptySet(),
        random: Random = Random.Default,
    ): EquivalencyMatch? {
        if (usageMinutes <= 0 || units.isEmpty()) return null
        val inCategory = if (category == EquivalencyCategory.MIXED) {
            units
        } else {
            units.filter { it.category == category.key }.ifEmpty { units }
        }

        val windows = listOf(PREFERRED_MIN to PREFERRED_MAX, WIDENED_MIN to WIDENED_MAX)
        for ((min, max) in windows) {
            for (allowExcluded in listOf(false, true)) {
                val pool = inCategory
                    .filter { allowExcluded || it.id !in excludedUnitIds }
                    .filter { it.minutes > 0 }
                    .filter { usageMinutes / it.minutes in min..max }
                if (pool.isNotEmpty()) {
                    return match(usageMinutes, pool[random.nextInt(pool.size)])
                }
            }
        }
        // Nothing lands in either window: fall back to the closest unit by ratio
        // so the widget still says something true rather than nothing at all.
        val closest = inCategory.filter { it.minutes > 0 }
            .minByOrNull { abs(usageMinutes / it.minutes - 1.0) } ?: return null
        return match(usageMinutes, closest)
    }

    private fun match(usageMinutes: Int, unit: EquivalencyUnit): EquivalencyMatch {
        val ratio = usageMinutes / unit.minutes
        return EquivalencyMatch(
            unit = unit,
            ratio = ratio,
            displayRatio = formatRatio(ratio),
            asPercentage = ratio < 1.0,
        )
    }

    /**
     * The Bible's formatting rule: below 1 show a percentage when clearer, 1-9.9
     * takes one decimal, 10 and above rounds to an integer.
     */
    fun formatRatio(ratio: Double): String = when {
        ratio < 1.0 -> "${Formatting.percent(ratio * 100)}%"
        else -> Formatting.oneDecimal(ratio)
    }

    fun variables(usageMinutes: Int, match: EquivalencyMatch?): Map<String, String> {
        val base = mapOf(
            "minutes" to Formatting.integer(usageMinutes.toLong()),
            "hours_short" to Formatting.hoursShort(usageMinutes),
        )
        return if (match == null) {
            base
        } else {
            base + mapOf(
                "ratio" to match.displayRatio,
                "equivalent" to match.unit.label,
            )
        }
    }
}
