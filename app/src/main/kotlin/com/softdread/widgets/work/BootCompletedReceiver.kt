package com.softdread.widgets.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Rebuilds the refresh schedule after a reboot, an app update, or a clock or
 * timezone change — all of which invalidate the midnight rollover's timing.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                WidgetRefreshScheduler.scheduleDailyRollover(context.applicationContext)
                WidgetRefreshScheduler.scheduleWeatherRefresh(context.applicationContext)
            }
        }
    }
}
