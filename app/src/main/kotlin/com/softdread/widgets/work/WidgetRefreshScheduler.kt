package com.softdread.widgets.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.softdread.widgets.domain.model.WidgetType
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns the app's background schedule. Two jobs, both cheap:
 *
 *  - a daily one-shot that fires just after local midnight so Daily Joke rolls
 *    over and Time Progress starts the new day at 0% rather than lingering at
 *    99.9%. It re-arms itself, which is also how it survives a timezone change;
 *  - a periodic weather refresh that only runs when the network is actually
 *    connected, so an offline device never burns a wakeup on a doomed request.
 *
 * Everything else is driven by the platform's own widget update period or by a
 * broadcast, per the Content Bible's "do not wake the device just to change
 * prose".
 */
object WidgetRefreshScheduler {

    private const val DAILY_ROLLOVER_WORK = "soft_dread_daily_rollover"
    private const val WEATHER_REFRESH_WORK = "soft_dread_weather_refresh"

    suspend fun scheduleAll(context: Context) = withContext(Dispatchers.Default) {
        scheduleDailyRollover(context)
        scheduleWeatherRefresh(context)
    }

    fun scheduleDailyRollover(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusMinutes(1)
        val delay = Duration.between(now, nextMidnight).coerceAtLeast(Duration.ofMinutes(1))

        val request = OneTimeWorkRequestBuilder<DailyRolloverWorker>()
            .setInitialDelay(delay)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(DAILY_ROLLOVER_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun scheduleWeatherRefresh(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(Duration.ofMinutes(60))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(
                workDataOf(WidgetRefreshWorker.KEY_WIDGET_TYPES to arrayOf(WidgetType.WEATHER.id)),
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WEATHER_REFRESH_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Refreshes one widget type immediately, used after a settings change. */
    fun refreshNow(context: Context, types: List<WidgetType>) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInputData(
                workDataOf(WidgetRefreshWorker.KEY_WIDGET_TYPES to types.map { it.id }.toTypedArray()),
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
