package com.softdread.widgets.widgets.common

import android.content.Context
import android.content.res.Configuration
import androidx.glance.GlanceId
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.softdread.widgets.core.time.Clock
import com.softdread.widgets.core.time.SystemClock
import com.softdread.widgets.data.content.ContentRepository
import com.softdread.widgets.data.prefs.AppearanceMode
import com.softdread.widgets.data.prefs.GlobalPreferences
import com.softdread.widgets.data.prefs.SoftDreadStore
import com.softdread.widgets.data.prefs.WidgetInstanceConfig
import com.softdread.widgets.design.SoftDreadTiles
import com.softdread.widgets.design.ThemePack
import com.softdread.widgets.domain.model.Personality
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.domain.selection.PoolHistory

/** Everything a widget needs to build its content, resolved before composition. */
data class WidgetEnvironment(
    val context: Context,
    val config: WidgetInstanceConfig,
    val preferences: GlobalPreferences,
    val personality: Personality,
    val isDark: Boolean,
    val breakpoints: List<WidgetBreakpoint>,
    val content: ContentRepository,
    val session: ContentSession,
    val clock: Clock,
)

/** The result of one widget update: what to draw at each size, and what it does when tapped. */
data class WidgetPayload(
    val contentByBreakpoint: Map<WidgetBreakpoint, TileContent>,
    val onClick: Action? = null,
)

/**
 * Base class for all eight widgets.
 *
 * It owns the parts every widget shares — resolving instance configuration and
 * the effective personality, loading anti-repeat history, choosing light or dark
 * treatment, and persisting the session's history writes — so each widget only
 * implements the part that is actually its own: turning device data into a
 * [TileContent].
 *
 * [SizeMode.Exact] rather than `Responsive`: the tile field is a bitmap, and
 * Responsive would build one RemoteViews per declared size, multiplying the
 * payload. Exact composes only for the sizes the host actually reports.
 */
abstract class SoftDreadWidget(protected val type: WidgetType) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    protected open val clock: Clock get() = SystemClock

    /** Builds the tile content for each breakpoint the host asked for. */
    protected abstract suspend fun buildPayload(environment: WidgetEnvironment): WidgetPayload

    /**
     * Runs the widget's real content pipeline without touching the home screen.
     *
     * The app's gallery and detail previews call this, so a preview is produced
     * by exactly the code that draws the placed widget — there is no second,
     * drifting implementation of what a tile says.
     */
    suspend fun render(environment: WidgetEnvironment): WidgetPayload = buildPayload(environment)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val manager = GlanceAppWidgetManager(context)
        val appWidgetId = manager.getAppWidgetId(id)
        val store = SoftDreadStore.get(context)
        val preferences = store.currentPreferences()
        val config = store.configOrCreate(appWidgetId, type)
        val personality = config.personality(preferences.defaultPersonality)
        val isDark = resolveDarkMode(context, config.appearance, preferences.appearance)

        val breakpoints = manager.getAppWidgetSizes(id)
            .map { WidgetBreakpoint.forSize(it.width.value, it.height.value) }
            .distinct()
            // Largest first, so the richest copy is chosen before the terser
            // variants and the session's anti-repeat history stays coherent.
            .sortedByDescending { it.widthDp * it.heightDp }
            .ifEmpty { listOf(WidgetBreakpoint.COMPACT) }

        val history: Map<String, PoolHistory> = store.history(config.instanceKey)
        val session = ContentSession(history, clock.now().toEpochMilli())

        val environment = WidgetEnvironment(
            context = context,
            config = config,
            preferences = preferences,
            personality = personality,
            isDark = isDark,
            breakpoints = breakpoints,
            content = ContentRepository.get(context),
            session = session,
            clock = clock,
        )

        val payload = buildPayload(environment)
        store.recordHistories(config.instanceKey, session.historyUpdates)

        val pack = ThemePack.fromKeyOrDefault(preferences.themePackKey)
        val colours = SoftDreadTiles.colours(type.colourRole, pack, isDark)
        val fallback = payload.contentByBreakpoint.values.firstOrNull() ?: return

        provideContent {
            val size = LocalSize.current
            val breakpoint = WidgetBreakpoint.forSize(size.width.value, size.height.value)
            SoftDreadTile(
                role = type.colourRole,
                breakpoint = breakpoint,
                colours = colours,
                content = payload.contentByBreakpoint[breakpoint] ?: fallback,
                onClick = payload.onClick,
            )
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        super.onDelete(context, glanceId)
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        SoftDreadStore.get(context).deleteInstance(appWidgetId)
    }

    /**
     * A per-instance appearance override wins over the global one, which in turn
     * wins over the system. This is what lets a single dark tile anchor an
     * otherwise light home screen, as the sheet suggests for Time Progress.
     */
    private fun resolveDarkMode(
        context: Context,
        instance: AppearanceMode,
        global: AppearanceMode,
    ): Boolean {
        val effective = if (instance != AppearanceMode.SYSTEM) instance else global
        return when (effective) {
            AppearanceMode.LIGHT -> false
            AppearanceMode.DARK -> true
            AppearanceMode.SYSTEM ->
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        }
    }
}

/** Builds the same [TileContent] for every requested breakpoint. */
fun WidgetEnvironment.sameForAllBreakpoints(
    build: (WidgetBreakpoint) -> TileContent,
): Map<WidgetBreakpoint, TileContent> = breakpoints.associateWith(build)

/**
 * The tile shown when a widget needs Usage Access, calendar permission or a
 * location before it can say anything true.
 *
 * The product rule is that a widget never fakes data: this state names what is
 * missing and stays tappable so the app can take the user straight to it.
 */
fun setupContent(
    label: String,
    headline: String,
    explanation: String,
    callToAction: String,
): TileContent = TileContent(
    label = label,
    heroValue = null,
    subhead = headline,
    voice = explanation,
    callToAction = callToAction,
    contentDescription = "$label. $headline. $explanation. $callToAction.",
    isSetupState = true,
)
