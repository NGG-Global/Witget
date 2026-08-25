package com.softdread.widgets.domain.logic

import com.google.common.truth.Truth.assertThat
import com.softdread.widgets.data.content.EquivalencyUnit
import com.softdread.widgets.data.prefs.EquivalencyCategory
import kotlin.random.Random
import org.junit.Test

/**
 * Screen Time band boundaries, equivalency matching and ratio formatting.
 *
 * The Bible's bands are <30, 30-89, 90-179, 180-299, 300-479 and >=480 minutes,
 * and its formatting rule is percentage below 1, one decimal from 1 to 9.9 and a
 * rounded integer from 10 up.
 */
class ScreenTimeLogicTest {

    private val units = listOf(
        EquivalencyUnit("ST01", "POP_CULTURE", 558.0, "the theatrical Lord of the Rings trilogy"),
        EquivalencyUnit("ST03", "ENTERTAINMENT", 120.0, "a feature-length movie"),
        EquivalencyUnit("ST05", "ENTERTAINMENT", 22.0, "a sitcom episode"),
        EquivalencyUnit("ST39", "AUDIO", 3.5, "an average song"),
        EquivalencyUnit("ST47", "SOCIAL", 0.5, "a 30-second short-form video"),
    )

    @Test
    fun `usage bands sit exactly on the Bible boundaries`() {
        assertThat(ScreenTimeLogic.stateKey(0)).isEqualTo("ST_B1")
        assertThat(ScreenTimeLogic.stateKey(29)).isEqualTo("ST_B1")
        assertThat(ScreenTimeLogic.stateKey(30)).isEqualTo("ST_B2")
        assertThat(ScreenTimeLogic.stateKey(89)).isEqualTo("ST_B2")
        assertThat(ScreenTimeLogic.stateKey(90)).isEqualTo("ST_B3")
        assertThat(ScreenTimeLogic.stateKey(179)).isEqualTo("ST_B3")
        assertThat(ScreenTimeLogic.stateKey(180)).isEqualTo("ST_B4")
        assertThat(ScreenTimeLogic.stateKey(299)).isEqualTo("ST_B4")
        assertThat(ScreenTimeLogic.stateKey(300)).isEqualTo("ST_B5")
        assertThat(ScreenTimeLogic.stateKey(479)).isEqualTo("ST_B5")
        assertThat(ScreenTimeLogic.stateKey(480)).isEqualTo("ST_B6")
        assertThat(ScreenTimeLogic.stateKey(1440)).isEqualTo("ST_B6")
    }

    @Test
    fun `ratio formatting follows the Bible rule`() {
        assertThat(ScreenTimeLogic.formatRatio(0.58)).isEqualTo("58%")
        assertThat(ScreenTimeLogic.formatRatio(0.995)).isEqualTo("100%")
        assertThat(ScreenTimeLogic.formatRatio(1.0)).isEqualTo("1")
        assertThat(ScreenTimeLogic.formatRatio(2.75)).isEqualTo("2.8")
        assertThat(ScreenTimeLogic.formatRatio(9.94)).isEqualTo("9.9")
        assertThat(ScreenTimeLogic.formatRatio(12.4)).isEqualTo("12")
        assertThat(ScreenTimeLogic.formatRatio(120.0)).isEqualTo("120")
    }

    @Test
    fun `never renders two decimals`() {
        // "Numeral takes one decimal, never two." — design sheet, Screen Time.
        repeat(200) { index ->
            val formatted = ScreenTimeLogic.formatRatio(index * 0.137)
            val decimals = formatted.substringAfter('.', "").removeSuffix("%")
            assertThat(decimals.length).isAtMost(1)
        }
    }

    @Test
    fun `chosen equivalency lands inside the preferred ratio window`() {
        val match = ScreenTimeLogic.chooseEquivalency(390, units, random = Random(7))
        assertThat(match).isNotNull()
        assertThat(match!!.ratio).isIn(com.google.common.collect.Range.closed(0.5, 12.0))
    }

    @Test
    fun `ratio maths is exact`() {
        val match = ScreenTimeLogic.chooseEquivalency(
            usageMinutes = 390,
            units = listOf(units.first()),
            random = Random(1),
        )
        assertThat(match!!.ratio).isWithin(1e-9).of(390.0 / 558.0)
        assertThat(match.asPercentage).isTrue()
        assertThat(match.displayRatio).isEqualTo("70%")
    }

    @Test
    fun `a category filter narrows the pool but never empties it`() {
        val match = ScreenTimeLogic.chooseEquivalency(
            usageMinutes = 200,
            units = units,
            category = EquivalencyCategory.GAMING,
            random = Random(3),
        )
        // No gaming unit exists in this fixture, so the whole library is used
        // rather than the widget saying nothing.
        assertThat(match).isNotNull()
    }

    @Test
    fun `excluded units are skipped while an alternative exists`() {
        val match = ScreenTimeLogic.chooseEquivalency(
            usageMinutes = 120,
            units = units,
            excludedUnitIds = setOf("ST03"),
            random = Random(11),
        )
        assertThat(match!!.unit.id).isNotEqualTo("ST03")
    }

    @Test
    fun `zero usage yields no equivalency`() {
        assertThat(ScreenTimeLogic.chooseEquivalency(0, units)).isNull()
    }

    @Test
    fun `variables always expose the literal metric`() {
        val variables = ScreenTimeLogic.variables(390, null)
        assertThat(variables["minutes"]).isEqualTo("390")
        assertThat(variables["hours_short"]).isEqualTo("6h 30m")
    }

    @Test
    fun `duration formatting is compact and correct`() {
        assertThat(Formatting.hoursShort(0)).isEqualTo("0 min")
        assertThat(Formatting.hoursShort(59)).isEqualTo("59 min")
        assertThat(Formatting.hoursShort(60)).isEqualTo("1h")
        assertThat(Formatting.hoursShort(61)).isEqualTo("1h 01m")
        assertThat(Formatting.hoursShort(125)).isEqualTo("2h 05m")
        assertThat(Formatting.hoursShort(1440)).isEqualTo("24h")
    }
}
