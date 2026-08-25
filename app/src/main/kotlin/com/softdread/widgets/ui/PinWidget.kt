package com.softdread.widgets.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.widgets.battery.BatteryWidgetReceiver
import com.softdread.widgets.widgets.countdown.CountdownWidgetReceiver
import com.softdread.widgets.widgets.dayvibe.DayVibeWidgetReceiver
import com.softdread.widgets.widgets.joke.DailyJokeWidgetReceiver
import com.softdread.widgets.widgets.magic8ball.Magic8BallWidgetReceiver
import com.softdread.widgets.widgets.progress.TimeProgressWidgetReceiver
import com.softdread.widgets.widgets.screentime.ScreenTimeWidgetReceiver
import com.softdread.widgets.widgets.weather.WeatherWidgetReceiver

/**
 * The "add to home screen" flow.
 *
 * Pinning is a launcher capability, not a guarantee: [isSupported] reports what
 * the current launcher actually offers so the UI can fall back to written
 * instructions instead of showing a button that silently does nothing.
 */
object PinWidget {

    fun isSupported(context: Context): Boolean =
        AppWidgetManager.getInstance(context)?.isRequestPinAppWidgetSupported == true

    suspend fun request(context: Context, type: WidgetType): Boolean {
        if (!isSupported(context)) return false
        return runCatching {
            GlanceAppWidgetManager(context).requestPinGlanceAppWidget(
                receiver = receiverFor(type),
                successCallback = successCallback(context, type),
            )
        }.getOrDefault(false)
    }

    /**
     * Fires after the launcher places the widget so the app can open its
     * configuration — Countdown in particular is useless until it knows what it
     * is counting to.
     */
    private fun successCallback(context: Context, type: WidgetType): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = "softdread://pinned/${type.id}".toUri()
            putExtra(EXTRA_PINNED_TYPE, type.id)
        }
        return PendingIntent.getActivity(
            context,
            type.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun receiverFor(type: WidgetType): Class<out GlanceAppWidgetReceiver> = when (type) {
        WidgetType.SCREEN_TIME -> ScreenTimeWidgetReceiver::class.java
        WidgetType.DAILY_JOKE -> DailyJokeWidgetReceiver::class.java
        WidgetType.BATTERY -> BatteryWidgetReceiver::class.java
        WidgetType.DAY_VIBE -> DayVibeWidgetReceiver::class.java
        WidgetType.WEATHER -> WeatherWidgetReceiver::class.java
        WidgetType.COUNTDOWN -> CountdownWidgetReceiver::class.java
        WidgetType.TIME_PROGRESS -> TimeProgressWidgetReceiver::class.java
        WidgetType.MAGIC_8_BALL -> Magic8BallWidgetReceiver::class.java
    }

    const val EXTRA_PINNED_TYPE = "com.softdread.widgets.extra.PINNED_TYPE"
}
