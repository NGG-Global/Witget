package com.softdread.widgets.work

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.softdread.widgets.data.prefs.SoftDreadStore
import com.softdread.widgets.data.weather.WeatherRepository
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.widgets.common.forceRefresh
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
            if (WidgetType.WEATHER in requested) refreshWeatherCache()
            requested.forEach { refresh(it) }
            pruneOrphanedInstances()
            Result.success()
        }.getOrElse { Result.retry() }
    }

    /**
     * Fetches each placed Weather instance's forecast before the tiles rebuild.
     *
     * The network belongs here and not in the widget: this worker runs under a
     * `CONNECTED` constraint with no broadcast deadline over it, so a slow
     * request costs a little battery rather than risking an ANR. The tiles then
     * render from the cache this fills.
     */
    private suspend fun refreshWeatherCache() {
        val store = SoftDreadStore.get(applicationContext)
        val preferences = store.currentPreferences()
        val repository = WeatherRepository(applicationContext)
        val instances = store.placedConfigs().filter { it.widgetType == WidgetType.WEATHER }
        // One request per distinct place, however many widgets point at it.
        instances
            .map { (it.savedLocation ?: preferences.defaultLocation) to it.useDeviceLocation }
            .distinct()
            .forEach { (location, useDeviceLocation) ->
                runCatching {
                    repository.state(
                        savedLocation = location,
                        useDeviceLocation = useDeviceLocation,
                        forceRefresh = true,
                    )
                }
            }
    }

    private suspend fun refresh(type: WidgetType) {
        val widget = widgetFor(type)
        val manager = GlanceAppWidgetManager(applicationContext)
        manager.getGlanceIds(widget.javaClass).forEach { id ->
            widget.forceRefresh(applicationContext, id)
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
        // An empty set is ambiguous: it means either "no widgets are placed" or
        // "the host could not be queried this time". Pruning on the second
        // reading would delete every instance's configuration and history, so
        // the platform is asked directly before anything is removed.
        if (live.isEmpty() && hasPlacedWidgets()) return
        SoftDreadStore.get(applicationContext).pruneOrphans(live)
    }

    /** Whether the platform still knows about any provider in the pack. */
    private fun hasPlacedWidgets(): Boolean {
        val manager = android.appwidget.AppWidgetManager.getInstance(applicationContext) ?: return true
        return WidgetType.entries.any { type ->
            val component = android.content.ComponentName(
                applicationContext,
                com.softdread.widgets.ui.PinWidget.receiverFor(type),
            )
            manager.getAppWidgetIds(component)?.isNotEmpty() == true
        }
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
