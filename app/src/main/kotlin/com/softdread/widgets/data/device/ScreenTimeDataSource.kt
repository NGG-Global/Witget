package com.softdread.widgets.data.device

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.time.ZoneId
import java.time.ZonedDateTime

/** Today's foreground usage, or the reason it is unavailable. */
sealed interface ScreenTimeResult {
    data class Available(val minutesToday: Int) : ScreenTimeResult
    /** Usage Access has not been granted. Never treated as zero usage. */
    data object PermissionRequired : ScreenTimeResult
    data class Unavailable(val reason: String) : ScreenTimeResult
}

/**
 * Reads today's foreground screen time via [UsageStatsManager].
 *
 * Usage Access is a special permission that cannot be requested with a runtime
 * dialog; the user has to grant it in Settings. [hasUsageAccess] reports the
 * real state and [settingsIntent] deep-links to the right screen, so the widget
 * can say "setup needed" honestly instead of showing a plausible zero.
 *
 * Total foreground time is computed from `queryEvents` rather than from
 * `queryUsageStats` buckets, because the daily bucket is only refreshed
 * periodically and undercounts the current day.
 */
class ScreenTimeDataSource(private val context: Context) {

    /**
     * AppOps is the only public way to test for Usage Access. Both check methods
     * carry deprecation warnings on recent SDKs but no replacement exists for
     * this op, and the framework itself still routes the Settings toggle through
     * them, so the suppression is deliberate rather than an oversight.
     */
    @Suppress("DEPRECATION")
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT -> context.checkCallingOrSelfPermission(
                android.Manifest.permission.PACKAGE_USAGE_STATS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }

    /**
     * The Settings screen that grants Usage Access. The per-app variant is
     * offered first because it lands the user directly on this app's toggle, but
     * several OEM builds do not implement it, so the generic list is the
     * fallback.
     */
    fun settingsIntent(): Intent {
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        val canResolveDirect = direct.resolveActivity(context.packageManager) != null
        return if (canResolveDirect) direct else Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }

    fun readToday(zone: ZoneId = ZoneId.systemDefault(), now: ZonedDateTime = ZonedDateTime.now(zone)): ScreenTimeResult {
        if (!hasUsageAccess()) return ScreenTimeResult.PermissionRequired
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return ScreenTimeResult.Unavailable("usage stats service unavailable")

        val startOfDay = now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val nowMillis = now.toInstant().toEpochMilli()
        if (nowMillis <= startOfDay) return ScreenTimeResult.Available(0)

        return runCatching { ScreenTimeResult.Available(foregroundMinutes(manager, startOfDay, nowMillis)) }
            .getOrElse { ScreenTimeResult.Unavailable(it.message ?: "usage query failed") }
    }

    /**
     * Sums time between each package's resume and its matching pause.
     *
     * Only one package is foreground at a time, so a resume for a new package
     * implicitly ends the previous one; tracking the single active package
     * avoids double-counting when an app is killed without emitting a pause.
     */
    private fun foregroundMinutes(manager: UsageStatsManager, start: Long, end: Long): Int {
        val events = manager.queryEvents(start, end)
        val event = UsageEvents.Event()
        var totalMillis = 0L
        var activePackage: String? = null
        var activeSince = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (activePackage != null && event.timeStamp > activeSince) {
                        totalMillis += event.timeStamp - activeSince
                    }
                    activePackage = event.packageName
                    activeSince = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                -> {
                    if (activePackage == event.packageName && event.timeStamp > activeSince) {
                        totalMillis += event.timeStamp - activeSince
                    }
                    activePackage = null
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.DEVICE_SHUTDOWN,
                -> {
                    if (activePackage != null && event.timeStamp > activeSince) {
                        totalMillis += event.timeStamp - activeSince
                    }
                    activePackage = null
                }
            }
        }
        // An app still in the foreground when the query window closes.
        if (activePackage != null && end > activeSince) {
            totalMillis += end - activeSince
        }
        return (totalMillis / 60_000L).toInt().coerceAtLeast(0)
    }
}
