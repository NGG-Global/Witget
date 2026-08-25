package com.softdread.widgets.ui.preview

import android.content.Context
import com.softdread.widgets.core.time.Clock
import com.softdread.widgets.core.time.SystemClock
import com.softdread.widgets.data.content.ContentRepository
import com.softdread.widgets.data.prefs.GlobalPreferences
import com.softdread.widgets.data.prefs.WidgetInstanceConfig
import com.softdread.widgets.domain.model.Personality
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.widgets.common.ContentSession
import com.softdread.widgets.widgets.common.TileContent
import com.softdread.widgets.widgets.common.WidgetEnvironment
import com.softdread.widgets.work.WidgetRefreshWorker

/** A preview tile plus whether it is showing live data or a labelled sample. */
data class WidgetPreview(
    val content: TileContent,
    val needsSetup: Boolean,
    val isSample: Boolean,
)

/**
 * Builds gallery and detail previews by running each widget's real pipeline.
 *
 * Nothing is persisted: the session's history writes are discarded, so opening
 * the gallery never consumes a widget's anti-repeat pool or changes what a
 * placed widget will say next.
 *
 * When a widget cannot show live data — Usage Access not granted, no calendar
 * permission, no weather location — the preview reports [WidgetPreview.needsSetup]
 * so the card can say so, and callers can ask for a clearly-labelled sample
 * instead via [SampleData].
 */
object WidgetPreviewer {

    suspend fun preview(
        context: Context,
        type: WidgetType,
        config: WidgetInstanceConfig,
        preferences: GlobalPreferences,
        breakpoint: WidgetBreakpoint,
        isDark: Boolean,
        clock: Clock = SystemClock,
    ): WidgetPreview {
        val widget = WidgetRefreshWorker.widgetFor(type)
        val environment = WidgetEnvironment(
            context = context,
            config = config,
            preferences = preferences,
            personality = config.personality(preferences.defaultPersonality),
            isDark = isDark,
            breakpoints = listOf(breakpoint),
            content = ContentRepository.get(context),
            // A throwaway session: previews must not consume anti-repeat history.
            session = ContentSession(emptyMap(), clock.now().toEpochMilli()),
            clock = clock,
        )

        @Suppress("TooGenericExceptionCaught")
        val payload = runCatching {
            (widget as com.softdread.widgets.widgets.common.SoftDreadWidget).render(environment)
        }.getOrNull()

        val content = payload?.contentByBreakpoint?.get(breakpoint)
            ?: payload?.contentByBreakpoint?.values?.firstOrNull()
            ?: return WidgetPreview(SampleData.content(type, breakpoint), needsSetup = true, isSample = true)

        return WidgetPreview(content, needsSetup = content.isSetupState, isSample = false)
    }

    /** A preview that always shows something representative, sample or not. */
    suspend fun previewOrSample(
        context: Context,
        type: WidgetType,
        config: WidgetInstanceConfig,
        preferences: GlobalPreferences,
        breakpoint: WidgetBreakpoint,
        isDark: Boolean,
        personality: Personality = config.personality(preferences.defaultPersonality),
    ): WidgetPreview {
        val live = preview(context, type, config, preferences, breakpoint, isDark)
        if (!live.needsSetup) return live
        return WidgetPreview(
            content = SampleData.content(type, breakpoint, personality),
            needsSetup = true,
            isSample = true,
        )
    }
}
