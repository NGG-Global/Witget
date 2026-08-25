package com.softdread.widgets.widgets.magic8ball

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import com.softdread.widgets.data.content.pool
import com.softdread.widgets.domain.logic.Magic8BallLogic
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.domain.selection.AntiRepeatPolicies
import com.softdread.widgets.widgets.common.LeadingVisual
import com.softdread.widgets.widgets.common.SoftDreadWidget
import com.softdread.widgets.widgets.common.TileContent
import com.softdread.widgets.widgets.common.WidgetEnvironment
import com.softdread.widgets.widgets.common.WidgetPayload
import com.softdread.widgets.widgets.common.forceRefresh
import kotlin.random.Random

/**
 * Magic 8 Ball.
 *
 * The one interactive tile in the pack. A tap runs [AskAgainAction], which
 * simply asks Glance to update the widget; because every update draws a new
 * answer, the tap reads as a re-roll without any bespoke state plumbing.
 *
 * The design sheet asks for a 120 ms crossfade on tap. Glance has no animation
 * API and the sheet's own platform note says so ("NO CUSTOM ANIMATION APIS,
 * 8 BALL = ACTIONCALLBACK ONLY"), so the answer swaps instantly rather than
 * through a fragile workaround.
 *
 * Sentiment is weighted 40/20/40 and the previous 12 answers per instance are
 * excluded, both per the Content Bible.
 */
class Magic8BallWidget : SoftDreadWidget(WidgetType.MAGIC_8_BALL) {

    override suspend fun buildPayload(environment: WidgetEnvironment): WidgetPayload {
        val document = environment.content.document(WidgetType.MAGIC_8_BALL)
        val random = Random(environment.clock.now().toEpochMilli() xor environment.config.appWidgetId.toLong())
        val preferred = Magic8BallLogic.chooseSentiment(random)

        // The 12-tap exclusion can empty a sentiment class, so fall through the
        // other classes rather than repeating an answer the user just saw.
        val answer = Magic8BallLogic.sentimentFallbackOrder(preferred).firstNotNullOfOrNull { sentiment ->
            environment.session.select(
                poolKey = ANSWER_POOL,
                candidates = document.pool(sentiment.stateKey, environment.personality),
                policy = AntiRepeatPolicies.MAGIC_8_BALL,
                maxChars = environment.breakpoints.minOf { it.maxResponseChars },
            )
        }

        val text = answer?.text ?: "The oracle is out of answers."

        val content = environment.breakpoints.associateWith { breakpoint ->
            TileContent(
                label = if (breakpoint.isLarge) "magic 8 ball" else "8 ball",
                voice = text,
                // The ball itself is the tile's one circle — the night field's
                // decorative circles are removed in TileGeometry so this widget
                // reads as the actual object.
                leading = LeadingVisual.EightBall,
                callToAction = "tap to ask again",
                contentDescription = "Magic 8 ball says: $text. Double tap to ask again.",
            )
        }

        return WidgetPayload(content, onClick = actionRunCallback<AskAgainAction>())
    }

    companion object {
        const val ANSWER_POOL = "magic8ball:answers"
    }
}

/**
 * Draws a new answer. Goes through [forceRefresh] because a bare `update()`
 * only recomposes the session's stale model — the tap would do nothing until
 * the session expired. Nothing is persisted beyond the anti-repeat history, so
 * the tile never stores a question or anything about the person asking it.
 */
class AskAgainAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Magic8BallWidget().forceRefresh(context, glanceId)
    }
}

class Magic8BallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = Magic8BallWidget()
}

/** Re-rolls every placed 8 ball; used by the app's detail screen. */
suspend fun refreshMagic8Ball(context: Context) {
    val widget = Magic8BallWidget()
    GlanceAppWidgetManager(context).getGlanceIds(Magic8BallWidget::class.java).forEach { id ->
        widget.forceRefresh(context, id)
    }
}
