package com.softdread.widgets.widgets.common

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.ui.MainActivity

/**
 * Tapping any tile opens the app on that widget's detail screen, which is where
 * its personality, appearance and widget-specific settings live. Setup states
 * land on the same screen, where the relevant permission explanation sits.
 */
fun openAppAction(context: Context, type: WidgetType, appWidgetId: Int? = null): Action {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_WIDGET_TYPE, type.id)
        appWidgetId?.let { putExtra(EXTRA_APP_WIDGET_ID, it) }
        // A distinct data URI keeps PendingIntents for different widgets from
        // being collapsed into one by the system.
        data = "softdread://widget/${type.id}/${appWidgetId ?: 0}".toUri()
    }
    return actionStartActivity(intent)
}

const val EXTRA_WIDGET_TYPE = "com.softdread.widgets.extra.WIDGET_TYPE"
const val EXTRA_APP_WIDGET_ID = "com.softdread.widgets.extra.APP_WIDGET_ID"
