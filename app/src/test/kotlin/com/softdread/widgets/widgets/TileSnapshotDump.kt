package com.softdread.widgets.widgets

import androidx.test.core.app.ApplicationProvider
import com.softdread.widgets.design.SoftDreadTiles
import com.softdread.widgets.design.ThemePack
import com.softdread.widgets.domain.model.Personality
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.ui.preview.SampleData
import com.softdread.widgets.widgets.common.LeadingVisual
import com.softdread.widgets.widgets.common.TileContent
import com.softdread.widgets.widgets.common.TileRenderer
import java.io.File
import java.io.FileOutputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Not an assertion — dumps rendered tiles to PNG under the build directory so a
 * human (or a reviewing session) can look at what the renderer actually draws.
 * Robolectric's native graphics mode runs real Skia, so these are faithful.
 *
 *     ./gradlew :app:testDebugUnitTest --tests '*TileSnapshotDump'
 *     ls app/build/tile-snapshots/
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TileSnapshotDump {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun dumpRepresentativeTiles() {
        val out = File("build/tile-snapshots").apply { mkdirs() }
        fun save(name: String, type: WidgetType, breakpoint: WidgetBreakpoint, dark: Boolean, content: TileContent) {
            val colours = SoftDreadTiles.colours(type.colourRole, ThemePack.CLAY_HOUSE, dark)
            val w = breakpoint.widthDp * 3
            val h = breakpoint.heightDp * 3
            val bitmap = TileRenderer.render(context, type.colourRole, breakpoint, colours, content, w, h)
            FileOutputStream(File(out, "$name.png")).use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }

        // The historically unreadable case: clay compact, voice over the circle.
        save("screentime_compact", WidgetType.SCREEN_TIME, WidgetBreakpoint.COMPACT, false,
            SampleData.content(WidgetType.SCREEN_TIME, WidgetBreakpoint.COMPACT))
        save("screentime_expanded", WidgetType.SCREEN_TIME, WidgetBreakpoint.EXPANDED, false,
            SampleData.content(WidgetType.SCREEN_TIME, WidgetBreakpoint.EXPANDED))
        save("battery_compact", WidgetType.BATTERY, WidgetBreakpoint.COMPACT, false,
            TileContent(
                label = "battery",
                heroValue = "100%",
                voice = "optimism is no longer appropriate",
                leading = LeadingVisual.Ring(1f),
                contentDescription = "d",
            ))
        save("battery_standard", WidgetType.BATTERY, WidgetBreakpoint.STANDARD, false,
            TileContent(
                label = "battery prognosis",
                heroValue = "23%",
                metric = "~ 2h 10m",
                voice = "optimism is no longer appropriate.",
                leading = LeadingVisual.Ring(0.23f),
                contentDescription = "d",
            ))
        save("dayvibe_standard_dark", WidgetType.DAY_VIBE, WidgetBreakpoint.STANDARD, true,
            SampleData.content(WidgetType.DAY_VIBE, WidgetBreakpoint.STANDARD))
        save("weather_compact", WidgetType.WEATHER, WidgetBreakpoint.COMPACT, false,
            SampleData.content(WidgetType.WEATHER, WidgetBreakpoint.COMPACT))
        save("weather_expanded_dark", WidgetType.WEATHER, WidgetBreakpoint.EXPANDED, true,
            SampleData.content(WidgetType.WEATHER, WidgetBreakpoint.EXPANDED))
        save("joke_standard", WidgetType.DAILY_JOKE, WidgetBreakpoint.STANDARD, false,
            TileContent(
                label = "daily joke",
                voice = "Why don't skeletons ever fight each other? They don't have the guts.",
                chips = listOf(
                    com.softdread.widgets.widgets.common.TileChip("dad approval 91%"),
                    com.softdread.widgets.widgets.common.TileChip("regret: medium", emphasised = true),
                ),
                contentDescription = "d",
            ))
        save("countdown_expanded", WidgetType.COUNTDOWN, WidgetBreakpoint.EXPANDED, false,
            SampleData.content(WidgetType.COUNTDOWN, WidgetBreakpoint.EXPANDED))
        save("progress_standard", WidgetType.TIME_PROGRESS, WidgetBreakpoint.STANDARD, false,
            SampleData.content(WidgetType.TIME_PROGRESS, WidgetBreakpoint.STANDARD))
        save("ball_compact", WidgetType.MAGIC_8_BALL, WidgetBreakpoint.COMPACT, false,
            TileContent(
                label = "8 ball",
                voice = "Signs point to yes, reluctantly.",
                leading = LeadingVisual.EightBall,
                callToAction = "tap to ask again",
                contentDescription = "d",
            ))
        save("ball_expanded", WidgetType.MAGIC_8_BALL, WidgetBreakpoint.EXPANDED, true,
            TileContent(
                label = "magic 8 ball",
                voice = "ABSOLUTELY NOT. TIMELINE PROTECTED.",
                leading = LeadingVisual.EightBall,
                callToAction = "tap to ask again",
                contentDescription = "d",
            ))
    }
}
