package com.softdread.widgets.data.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.softdread.widgets.domain.logic.BatteryReading

/**
 * Reads real battery state from the platform.
 *
 * Two deliberate choices here. First, the current level comes from a sticky
 * `ACTION_BATTERY_CHANGED` broadcast read on demand rather than from a
 * registered receiver that polls — the Bible's rule is "avoid timer polling",
 * and a sticky read costs nothing. Second, the estimated time remaining is only
 * reported when the platform actually supplies one; the Bible is explicit that
 * an estimate must never be fabricated from percentage alone.
 */
class BatteryDataSource(private val context: Context) {

    fun read(): BatteryReading {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) {
            (level * 100f / scale).toInt().coerceIn(0, 100)
        } else {
            batteryManager()?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.coerceIn(0, 100)
                ?: 0
        }

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        return BatteryReading(
            percent = percent,
            isCharging = isCharging,
            minutesRemaining = estimateMinutesRemaining(isCharging),
        )
    }

    /**
     * `computeChargeTimeRemaining` is the only remaining-time estimate the
     * platform exposes and it covers charging only; it also returns -1 whenever
     * the OS has not gathered enough data. Discharge estimates are therefore
     * reported as absent rather than invented.
     */
    private fun estimateMinutesRemaining(isCharging: Boolean): Int? {
        if (!isCharging) return null
        // computeChargeTimeRemaining arrived in API 28; below that the platform
        // has no estimate to offer and the widget shows none.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val millis = runCatching { batteryManager()?.computeChargeTimeRemaining() }.getOrNull() ?: return null
        if (millis <= 0L) return null
        return (millis / 60_000L).toInt().takeIf { it > 0 }
    }

    private fun batteryManager(): BatteryManager? =
        context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
}
