package com.softdread.widgets.domain.logic

/** A battery reading as delivered by the platform. */
data class BatteryReading(
    val percent: Int,
    val isCharging: Boolean,
    /** Platform estimate in minutes, or `null` when the system has none. */
    val minutesRemaining: Int? = null,
)

/**
 * Maps a battery reading onto a Content Bible trigger state.
 *
 * The Bible's priority rules are explicit: charging always wins over the
 * discharge bands, so a phone at 12% that is plugged in reads as recovery rather
 * than as a crisis.
 *
 * Charging bands: 0-20, 21-60, 61-90, 91-100.
 * Discharge bands: 95-100, 80-94, 60-79, 40-59, 25-39, 15-24, 6-14, 1-5.
 */
object BatteryLogic {

    fun stateKey(reading: BatteryReading): String {
        val percent = reading.percent.coerceIn(0, 100)
        return if (reading.isCharging) chargingState(percent) else dischargeState(percent)
    }

    private fun chargingState(percent: Int): String = when {
        percent <= 20 -> "BAT_CH1"
        percent <= 60 -> "BAT_CH2"
        percent <= 90 -> "BAT_CH3"
        else -> "BAT_CH4"
    }

    /**
     * The Bible's lowest discharge band starts at 1%; a reading of 0% is folded
     * into it rather than left without copy.
     */
    private fun dischargeState(percent: Int): String = when {
        percent >= 95 -> "BAT_B1"
        percent >= 80 -> "BAT_B2"
        percent >= 60 -> "BAT_B3"
        percent >= 40 -> "BAT_B4"
        percent >= 25 -> "BAT_B5"
        percent >= 15 -> "BAT_B6"
        percent >= 6 -> "BAT_B7"
        else -> "BAT_B8"
    }

    /** True when the reading warrants keeping the charge prompt unmistakable. */
    fun isCritical(reading: BatteryReading): Boolean =
        !reading.isCharging && reading.percent <= 14

    fun variables(reading: BatteryReading): Map<String, String> =
        mapOf("percent" to Formatting.integer(reading.percent.coerceIn(0, 100).toLong()))
}
