package com.softdread.widgets.work

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.softdread.widgets.data.prefs.SoftDreadStore
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.widgets.battery.BatteryWidget
import com.softdread.widgets.widgets.countdown.CountdownWidget
import com.softdread.widgets.widgets.dayvibe.DayVibeWidget
import com.softdread.widgets.widgets.joke.DailyJokeWidget
import com.softdread.widgets.widgets.magic8ball.Magic8BallWidget
import com.softdread.widgets.widgets.progress.TimeProgressWidget
import com.softdread.widgets.widgets.screentime.ScreenTimeWidget
import com.softdread.widgets.widgets.weather.WeatherWidget

/**
 * Refreshes every placed widget of the requested types.
 *
 * Widgets that only need the clock (`updatePeriodMillis` in their provider XML)
 * are refreshed by the platform; this worker exists for the two cases the
 * platform cannot express — the local-midnight rollover that Daily Joke and
 * Time Progress need, and a network-constrained weather refresh that does not
 * fire when the device is offline.
 */
class WidgetRefreshWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val requested = inputData.getStringArray(KEY_WIDGET_TYPES)
            ?.mapNotNull { WidgetType.fromId(it) }
            ?: WidgetType.entries
        return runCatching {
            requested.forEach { refresh(it) }
            pruneOrphanedInstances()
            Result.success()
        }.getOrElse { Result.retry() }
    }

    private suspend fun refresh(type: WidgetType) {
        val widget = widgetFor(type)
        val manager = GlanceAppWidgetManager(applicationContext)
        manager.getGlanceIds(widget.javaClass).forEach { id ->
            widget.update(applicationContext, id)
        }
    }

    /**
     * Configuration for widgets the host no longer knows about is dropped here
     * as well as on delete, because a restore from backup or a launcher crash
     * can leave records behind without an `ACTION_APPWIDGET_DELETED`.
     */
    private suspend fun pruneOrphanedInstances() {
        val manager = GlanceAppWidgetManager(applicationContext)
        val live = WidgetType.entries.flatMap { type ->
            manager.getGlanceIds(widgetFor(type).javaClass).map { manager.getAppWidgetId(it) }
        }.toSet()
        SoftDreadStore.get(applicationContext).pruneOrphans(live)
    }

    companion object {
        const val KEY_WIDGET_TYPES = "widget_types"

        fun widgetFor(type: WidgetType): GlanceAppWidget = when (type) {
            WidgetType.SCREEN_TIME -> ScreenTimeWidget()
            WidgetType.DAILY_JOKE -> DailyJokeWidget()
            WidgetType.BATTERY -> BatteryWidget()
            WidgetType.DAY_VIBE -> DayVibeWidget()
            WidgetType.WEATHER -> WeatherWidget()
            WidgetType.COUNTDOWN -> CountdownWidget()
            WidgetType.TIME_PROGRESS -> TimeProgressWidget()
            WidgetType.MAGIC_8_BALL -> Magic8BallWidget()
        }
    }
}
