package com.softdread.widgets.widgets.common

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.work.WidgetRefreshWorker

/**
 * The tap action for data widgets: rebuild this widget in place.
 *
 * A tap re-reads the device data and draws a fresh personality line — the
 * anti-repeat engine guarantees the copy actually changes — which is what makes
 * a tile feel alive rather than like a static picture. Widgets whose tap should
 * open the app (setup states, Countdown's configuration, the Daily Joke, whose
 * content is deliberately stable all day) use [openAppAction] instead.
 */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val type = WidgetType.fromId(parameters[TYPE_ID_KEY]) ?: return
        WidgetRefreshWorker.widgetFor(type).forceRefresh(context, glanceId)
    }

    companion object {
        val TYPE_ID_KEY = ActionParameters.Key<String>("widget_type_id")
    }
}

/** A tap that rebuilds the widget in place. */
fun refreshAction(type: WidgetType): Action = actionRunCallback<RefreshWidgetAction>(
    actionParametersOf(RefreshWidgetAction.TYPE_ID_KEY to type.id),
)
