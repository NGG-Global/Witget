package com.softdread.widgets.widgets.common

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import com.softdread.widgets.core.time.Clock
import com.softdread.widgets.core.time.SystemClock
import com.softdread.widgets.data.content.ContentRepository
import com.softdread.widgets.data.prefs.AppearanceMode
import com.softdread.widgets.data.prefs.GlobalPreferences
import com.softdread.widgets.data.prefs.SoftDreadStore
import com.softdread.widgets.data.prefs.WidgetInstanceConfig
import com.softdread.widgets.design.SoftDreadTiles
import com.softdread.widgets.design.ThemePack
import com.softdread.widgets.design.TileColours
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

/** The result of one widget update: what to draw at each size, and what a tap does. */
data class WidgetPayload(
    val contentByBreakpoint: Map<WidgetBreakpoint, TileContent>,
    val onClick: Action? = null,
)

/** One fully built render pass: payload plus the colours it should draw in. */
private data class RenderModel(
    val payload: WidgetPayload,
    val colours: TileColours,
)

/**
 * Base class for all eight widgets.
 *
 * It owns what every widget shares — instance configuration, the effective
 * personality, anti-repeat history, light/dark resolution, persistence — so each
 * widget implements only [buildPayload]: turning device data into [TileContent].
 *
 * Two behaviours here are load-bearing and easy to regress:
 *
 * **Rebuild lives inside the composition.** Glance keeps a widget's composition
 * session alive between updates; `update()` recomposes but does not re-run
 * `provideGlance`. Anything computed outside `provideContent` is therefore
 * frozen for the session's lifetime — an early version built the payload there,
 * and the 8 ball ignored taps for as long as the session lived. The payload is
 * now rebuilt whenever [REFRESH_TICK] changes in the widget's Glance state;
 * [forceRefresh] bumps it and is the only correct way to refresh a widget.
 *
 * **Content is built for every breakpoint, every time.** With [SizeMode.Exact]
 * a resize recomposes with a new [LocalSize] but does not re-run the build. If
 * the map only held the sizes reported at build time, a resize could render
 * copy budgeted for a different tile and clip. Building all five breakpoints
 * costs a few string selections and makes any size the launcher lands on exact.
 */
abstract class SoftDreadWidget(protected val type: WidgetType) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    protected open val clock: Clock get() = SystemClock

    /** Builds the tile content for each breakpoint in [WidgetEnvironment.breakpoints]. */
    protected abstract suspend fun buildPayload(environment: WidgetEnvironment): WidgetPayload

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val first = buildFresh(context, appWidgetId)

        provideContent {
            val tick = currentState(REFRESH_TICK) ?: 0
            var model by remember { mutableStateOf(first) }
            var renderedTick by remember { mutableIntStateOf(tick) }

            LaunchedEffect(tick) {
                if (tick != renderedTick) {
                    model = buildFresh(context, appWidgetId)
                    renderedTick = tick
                }
            }

            val size = LocalSize.current
            val breakpoint = WidgetBreakpoint.forSize(size.width.value, size.height.value)
            val content = model.payload.contentByBreakpoint[breakpoint]
                ?: model.payload.contentByBreakpoint.values.first()
            SoftDreadTile(
                role = type.colourRole,
                breakpoint = breakpoint,
                colours = model.colours,
                content = content,
                onClick = model.payload.onClick,
            )
        }
    }

    /** One complete build: config, history, data, selection, persistence. */
    private suspend fun buildFresh(context: Context, appWidgetId: Int): RenderModel {
        val store = SoftDreadStore.get(context)
        val preferences = store.currentPreferences()
        val config = store.configOrCreate(appWidgetId, type)
        val personality = config.personality(preferences.defaultPersonality)
        val isDark = resolveDarkMode(context, config.appearance, preferences.appearance)

        val history: Map<String, PoolHistory> = store.history(config.instanceKey)
        val session = ContentSession(history, clock.now().toEpochMilli())

        val environment = WidgetEnvironment(
            context = context,
            config = config,
            preferences = preferences,
            personality = personality,
            isDark = isDark,
            // Largest first, so the richest copy is selected before the terser
            // variants and the session's anti-repeat history stays coherent.
            breakpoints = WidgetBreakpoint.entries.sortedByDescending { it.widthDp * it.heightDp },
            content = ContentRepository.get(context),
            session = session,
            clock = clock,
        )

        val payload = buildPayload(environment)
        store.recordHistories(config.instanceKey, session.historyUpdates)

        val pack = ThemePack.fromKeyOrDefault(preferences.themePackKey)
        return RenderModel(
            payload = payload,
            colours = SoftDreadTiles.colours(type.colourRole, pack, isDark),
        )
    }

    /**
     * Runs the real content pipeline without touching the home screen. The
     * app's previews call this, so a preview is produced by exactly the code
     * that draws the placed widget.
     */
    suspend fun render(environment: WidgetEnvironment): WidgetPayload = buildPayload(environment)

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        super.onDelete(context, glanceId)
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        SoftDreadStore.get(context).deleteInstance(appWidgetId)
    }

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

    companion object {
        /**
         * Incremented to request a rebuild inside the live composition session.
         * Wraps harmlessly; only inequality matters.
         */
        val REFRESH_TICK: Preferences.Key<Int> = intPreferencesKey("refresh_tick")
    }
}

/**
 * Rebuilds and redraws one placed widget.
 *
 * Bumping the tick is what makes the rebuild happen inside a live composition
 * session; a bare `update()` only recomposes the stale model. Every programmatic
 * refresh in the app goes through here.
 */
suspend fun GlanceAppWidget.forceRefresh(context: Context, glanceId: GlanceId) {
    updateAppWidgetState(context, glanceId) { prefs ->
        prefs[SoftDreadWidget.REFRESH_TICK] = (prefs[SoftDreadWidget.REFRESH_TICK] ?: 0) + 1
    }
    update(context, glanceId)
}

/** Builds the same [TileContent] for every requested breakpoint. */
fun WidgetEnvironment.sameForAllBreakpoints(
    build: (WidgetBreakpoint) -> TileContent,
): Map<WidgetBreakpoint, TileContent> = breakpoints.associateWith(build)

/**
 * The tile shown when a widget needs Usage Access, calendar permission or a
 * location before it can say anything true. Never fabricates data; stays
 * tappable so the app can take the user straight to the fix.
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
