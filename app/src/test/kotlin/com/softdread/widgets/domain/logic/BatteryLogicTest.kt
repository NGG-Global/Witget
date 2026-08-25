package com.softdread.widgets.domain.logic

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Battery band boundaries.
 *
 * The Content Bible's discharge bands are 95-100, 80-94, 60-79, 40-59, 25-39,
 * 15-24, 6-14 and 1-5, and charging always wins. Every adjacent pair below is
 * tested from both sides, because an off-by-one here silently swaps the tone of
 * the widget at exactly the moment the user is most likely to look at it.
 */
class BatteryLogicTest {

    private fun state(percent: Int, charging: Boolean = false) =
        BatteryLogic.stateKey(BatteryReading(percent, charging))

    @Test
    fun `discharge bands map to the Bible states`() {
        assertThat(state(100)).isEqualTo("BAT_B1")
        assertThat(state(95)).isEqualTo("BAT_B1")
        assertThat(state(94)).isEqualTo("BAT_B2")
        assertThat(state(80)).isEqualTo("BAT_B2")
        assertThat(state(79)).isEqualTo("BAT_B3")
        assertThat(state(60)).isEqualTo("BAT_B3")
        assertThat(state(59)).isEqualTo("BAT_B4")
        assertThat(state(40)).isEqualTo("BAT_B4")
        assertThat(state(39)).isEqualTo("BAT_B5")
        assertThat(state(25)).isEqualTo("BAT_B5")
        assertThat(state(24)).isEqualTo("BAT_B6")
        assertThat(state(15)).isEqualTo("BAT_B6")
        assertThat(state(14)).isEqualTo("BAT_B7")
        assertThat(state(6)).isEqualTo("BAT_B7")
        assertThat(state(5)).isEqualTo("BAT_B8")
        assertThat(state(1)).isEqualTo("BAT_B8")
    }

    @Test
    fun `zero percent falls into the lowest band rather than having no copy`() {
        assertThat(state(0)).isEqualTo("BAT_B8")
    }

    @Test
    fun `charging bands map to the Bible states`() {
        assertThat(state(0, charging = true)).isEqualTo("BAT_CH1")
        assertThat(state(20, charging = true)).isEqualTo("BAT_CH1")
        assertThat(state(21, charging = true)).isEqualTo("BAT_CH2")
        assertThat(state(60, charging = true)).isEqualTo("BAT_CH2")
        assertThat(state(61, charging = true)).isEqualTo("BAT_CH3")
        assertThat(state(90, charging = true)).isEqualTo("BAT_CH3")
        assertThat(state(91, charging = true)).isEqualTo("BAT_CH4")
        assertThat(state(100, charging = true)).isEqualTo("BAT_CH4")
    }

    @Test
    fun `charging takes priority over every discharge band`() {
        // Priority 1 in the Bible: a plugged-in phone at 12% is recovering,
        // not in crisis.
        assertThat(state(12, charging = true)).isEqualTo("BAT_CH1")
        assertThat(state(12, charging = false)).isEqualTo("BAT_B7")
    }

    @Test
    fun `out of range readings are clamped`() {
        assertThat(state(-5)).isEqualTo("BAT_B8")
        assertThat(state(150)).isEqualTo("BAT_B1")
    }

    @Test
    fun `critical is only reported while discharging at or below fourteen percent`() {
        assertThat(BatteryLogic.isCritical(BatteryReading(14, false))).isTrue()
        assertThat(BatteryLogic.isCritical(BatteryReading(15, false))).isFalse()
        assertThat(BatteryLogic.isCritical(BatteryReading(5, true))).isFalse()
    }

    @Test
    fun `percent variable is always present and clamped`() {
        assertThat(BatteryLogic.variables(BatteryReading(37, false))["percent"]).isEqualTo("37")
        assertThat(BatteryLogic.variables(BatteryReading(120, false))["percent"]).isEqualTo("100")
    }
}
