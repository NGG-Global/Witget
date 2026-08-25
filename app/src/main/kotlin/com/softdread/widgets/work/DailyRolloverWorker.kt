package com.softdread.widgets.work

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.softdread.widgets.domain.model.WidgetType

/**
 * Runs just after local midnight: rolls Daily Joke onto the new day's selection
 * and moves Time Progress, Day Vibe and Countdown onto the new date. It re-arms
 * itself for the next midnight, which also picks up a timezone change.
 */
class DailyRolloverWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val types = listOf(
            WidgetType.DAILY_JOKE,
            WidgetType.TIME_PROGRESS,
            WidgetType.DAY_VIBE,
            WidgetType.COUNTDOWN,
            WidgetType.SCREEN_TIME,
        )
        val result = runCatching {
            val manager = GlanceAppWidgetManager(applicationContext)
            types.forEach { type ->
                val widget = WidgetRefreshWorker.widgetFor(type)
                manager.getGlanceIds(widget.javaClass).forEach { id ->
                    widget.update(applicationContext, id)
                }
            }
        }
        // Re-arm before reporting, so a failed refresh does not end the schedule.
        WidgetRefreshScheduler.scheduleDailyRollover(applicationContext)
        return if (result.isSuccess) Result.success() else Result.retry()
    }
}
