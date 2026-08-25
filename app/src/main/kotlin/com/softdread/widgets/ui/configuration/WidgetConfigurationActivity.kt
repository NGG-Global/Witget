package com.softdread.widgets.ui.configuration

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.softdread.widgets.data.prefs.AppearanceMode
import com.softdread.widgets.data.prefs.SoftDreadStore
import com.softdread.widgets.data.prefs.WidgetInstanceConfig
import com.softdread.widgets.design.SoftDreadTheme
import com.softdread.widgets.design.ThemePack
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.work.WidgetRefreshWorker
import kotlinx.coroutines.launch

/**
 * The widget configuration activity.
 *
 * It serves every widget type from one screen and handles both flows Android
 * defines: first placement, where the result code decides whether the widget is
 * kept, and reconfiguration of an existing widget, where it is not.
 *
 * The result is set to `RESULT_CANCELED` before anything else, so backing out
 * removes a half-configured widget rather than leaving an empty tile behind.
 */
class WidgetConfigurationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setResult(Activity.RESULT_CANCELED, resultIntent(appWidgetId))

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            val store = remember { SoftDreadStore.get(applicationContext) }
            val scope = rememberCoroutineScope()
            var config by remember { mutableStateOf<WidgetInstanceConfig?>(null) }
            var preferences by remember {
                mutableStateOf(com.softdread.widgets.data.prefs.GlobalPreferences())
            }

            androidx.compose.runtime.LaunchedEffect(appWidgetId) {
                preferences = store.currentPreferences()
                val existing = store.currentConfig(appWidgetId)
                config = existing ?: WidgetInstanceConfig.default(
                    appWidgetId = appWidgetId,
                    type = typeForProvider(appWidgetId) ?: WidgetType.COUNTDOWN,
                )
            }

            val systemDark = isSystemInDarkTheme()
            val isDark = when (preferences.appearance) {
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
                AppearanceMode.SYSTEM -> systemDark
            }

            SoftDreadTheme(
                darkTheme = isDark,
                pack = ThemePack.fromKeyOrDefault(preferences.themePackKey),
            ) {
                config?.let { current ->
                    WidgetConfigurationScreen(
                        config = current,
                        preferences = preferences,
                        isDark = isDark,
                        onChange = { transform -> config = transform(current) },
                        onCancel = { finish() },
                        onSave = {
                            scope.launch {
                                store.saveConfig(current)
                                val type = current.widgetType
                                if (type != null) {
                                    val widget = WidgetRefreshWorker.widgetFor(type)
                                    val glanceId = GlanceAppWidgetManager(applicationContext)
                                        .getGlanceIdBy(appWidgetId)
                                    widget.update(applicationContext, glanceId)
                                }
                                setResult(Activity.RESULT_OK, resultIntent(appWidgetId))
                                finish()
                            }
                        },
                    )
                }
            }
        }
    }

    private fun resultIntent(appWidgetId: Int) =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    /**
     * Resolves which widget type this id belongs to by reading the provider the
     * host bound it to, so one configuration activity can serve all eight types.
     */
    private fun typeForProvider(appWidgetId: Int): WidgetType? {
        val info = AppWidgetManager.getInstance(this)?.getAppWidgetInfo(appWidgetId) ?: return null
        val className = info.provider?.className ?: return null
        return WidgetType.entries.firstOrNull { type ->
            className == com.softdread.widgets.ui.PinWidget.receiverFor(type).name
        }
    }
}
