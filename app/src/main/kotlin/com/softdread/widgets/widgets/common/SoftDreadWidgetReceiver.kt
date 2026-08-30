package com.softdread.widgets.widgets.common

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.softdread.widgets.work.WidgetRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The receiver every widget in the pack uses.
 *
 * It exists for two things `GlanceAppWidgetReceiver` alone does not do.
 *
 * **The platform's periodic update has to rebuild, not redraw.** Glance keeps a
 * widget's composition session alive for as long as the process lives, and
 * `GlanceAppWidgetReceiver.onUpdate` calls a bare `update()`. Inside a live
 * session that only recomposes the model the session is already holding — the
 * same trap documented on [SoftDreadWidget] — so every `updatePeriodMillis`
 * tick redrew yesterday's data. Bumping the refresh tick is what makes the
 * rebuild happen, so that is what happens here.
 *
 * **Declared broadcasts have to be handled.** `AppWidgetProvider.onReceive`
 * dispatches only the `APPWIDGET_*` actions and silently drops everything else,
 * so a receiver can subscribe to a broadcast in the manifest and do nothing
 * with it. [refreshActions] is the list of extra actions a widget wants, and
 * each one refreshes every placed instance of that widget.
 */
abstract class SoftDreadWidgetReceiver : GlanceAppWidgetReceiver() {

    /**
     * Extra broadcast actions this widget's manifest entry subscribes to. Each
     * one rebuilds every placed instance. Keep this in step with the receiver's
     * `intent-filter`: an action here that is not declared never arrives, and
     * one declared but not listed here is silently ignored.
     */
    protected open val refreshActions: Set<String> = emptySet()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // Glance's own onUpdate also registers this receiver with
        // GlanceAppWidgetManager, which the workers rely on to find instances,
        // so it still runs. The rebuild is what it does not do.
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        rebuild(context) { app ->
            val manager = GlanceAppWidgetManager(app)
            appWidgetIds.forEach { id ->
                runCatching { glanceAppWidget.forceRefresh(app, manager.getGlanceIdBy(id)) }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action !in refreshActions) return
        rebuild(context) { app ->
            val manager = GlanceAppWidgetManager(app)
            val ids = AppWidgetManager.getInstance(app)
                ?.getAppWidgetIds(ComponentName(app, javaClass))
                ?: IntArray(0)
            ids.forEach { id: Int ->
                runCatching { glanceAppWidget.forceRefresh(app, manager.getGlanceIdBy(id)) }
            }
        }
    }

    /**
     * The first widget of this type reaching a home screen is the earliest
     * moment the pack knows it has work to schedule. Arming here rather than
     * only from the app means a widget added straight from the launcher's
     * picker still rolls over at midnight and still refreshes its weather.
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val app = context.applicationContext
        WidgetRefreshScheduler.scheduleDailyRollover(app)
        WidgetRefreshScheduler.scheduleWeatherRefresh(app)
    }

    /**
     * Runs [block] past the end of `onReceive`. `goAsync` is what keeps the
     * process alive while the rebuild completes; without it the refresh races
     * the receiver's own teardown.
     */
    private fun rebuild(context: Context, block: suspend (Context) -> Unit) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                block(app)
            } finally {
                runCatching { pending.finish() }
            }
        }
    }
}
