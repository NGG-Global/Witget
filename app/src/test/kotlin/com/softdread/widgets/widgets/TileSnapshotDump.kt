package com.softdread.widgets.widgets

import androidx.test.core.app.ApplicationProvider
import com.softdread.widgets.design.SoftDreadTiles
import com.softdread.widgets.design.ThemePack
import com.softdread.widgets.domain.model.Personality
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.ui.preview.SampleData
import com.softdread.widgets.widgets.common.LeadingVisual
import com.softdread.widgets.widgets.common.MotifGlyph
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
        fun save(
            name: String,
            type: WidgetType,
            breakpoint: WidgetBreakpoint,
            dark: Boolean,
            content: TileContent,
            widthDp: Int = breakpoint.widthDp,
            heightDp: Int = breakpoint.heightDp,
        ) {
            // Same resolution the real pipeline uses: a content-matched
            // fieldRole retints the whole tile.
            val role = content.fieldRole ?: type.colourRole
            val colours = SoftDreadTiles.colours(role, ThemePack.CLAY_HOUSE, dark)
            val w = widthDp * 3
            val h = heightDp * 3
            val bitmap = TileRenderer.render(context, role, breakpoint, colours, content, w, h, densityPx = 3f)
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
        save("battery_hero", WidgetType.BATTERY, WidgetBreakpoint.HERO, false,
            TileContent(
                label = "battery prognosis",
                metric = "~ 2h 10m left",
                pill = "23% — optimism is no longer appropriate, but the charger is within reach.",
                leading = LeadingVisual.Ring(0.23f, centreLabel = "23%", centreDetail = "~ 2h 10m"),
                contentDescription = "d",
            ))
        save("weather_hero", WidgetType.WEATHER, WidgetBreakpoint.HERO, false,
            SampleData.content(WidgetType.WEATHER, WidgetBreakpoint.HERO))

        // The aspect ratios from the reported tablet screenshot: short-wide
        // tiles where the bottom stack used to collide with the label.
        save("regress_screentime", WidgetType.SCREEN_TIME, WidgetBreakpoint.EXPANDED, false,
            TileContent(
                label = "screen time",
                labelDetail = "33 min",
                heroValue = "4.1",
                subhead = "an aggressively long toaster cycle",
                pill = "You've spent 33 min on-screen today.",
                contentDescription = "d",
            ), widthDp = 500, heightDp = 290)
        save("regress_weather", WidgetType.WEATHER, WidgetBreakpoint.HERO, false,
            TileContent(
                label = "weather, translated",
                heroValue = "28°",
                metric = "feels 34° · high 31 · low 23",
                pill = "Warm and humid conditions.",
                strip = listOf(0.2f, 0.4f, 0.5f, 0.3f, 0.2f),
                satelliteRole = com.softdread.widgets.domain.model.ColourRole.AMBER,
                contentDescription = "d",
            ), widthDp = 560, heightDp = 320)
        save("regress_ball", WidgetType.MAGIC_8_BALL, WidgetBreakpoint.EXPANDED, true,
            TileContent(
                label = "magic 8 ball",
                voice = "Signs point to no.",
                leading = LeadingVisual.EightBall,
                callToAction = "tap to ask again",
                contentDescription = "d",
            ), widthDp = 420, heightDp = 280)
        save("regress_joke", WidgetType.DAILY_JOKE, WidgetBreakpoint.EXPANDED, false,
            TileContent(
                label = "daily joke",
                labelDetail = "25 aug",
                voice = "My work-life balance is currently buffering.",
                chips = listOf(
                    com.softdread.widgets.widgets.common.TileChip("dad energy: maximum"),
                    com.softdread.widgets.widgets.common.TileChip("absurdity index: 94%", emphasised = true),
                ),
                contentDescription = "d",
            ), widthDp = 470, heightDp = 290)
        save("ball_expanded", WidgetType.MAGIC_8_BALL, WidgetBreakpoint.EXPANDED, true,
            TileContent(
                label = "magic 8 ball",
                voice = "ABSOLUTELY NOT. TIMELINE PROTECTED.",
                leading = LeadingVisual.EightBall,
                callToAction = "tap to ask again",
                contentDescription = "d",
            ))

        // Content-matched motifs. The two canonical cases from the request:
        // the blue-paint joke arrives on a slate field with the roller, and
        // the toaster shows toast. Screen time keeps clay and gains the glyph.
        val paintJoke = "What is blue and smells like blue paint? Blue paint."
        listOf(
            WidgetBreakpoint.COMPACT to "motif_paint_compact",
            WidgetBreakpoint.STANDARD to "motif_paint_standard",
            WidgetBreakpoint.EXPANDED to "motif_paint_expanded",
        ).forEach { (bp, name) ->
            save(name, WidgetType.DAILY_JOKE, bp, false,
                TileContent(
                    label = "daily joke",
                    labelDetail = if (bp.isLarge) "26 aug" else null,
                    voice = paintJoke,
                    chips = if (bp != WidgetBreakpoint.TINY) {
                        listOf(com.softdread.widgets.widgets.common.TileChip("anti-joke certified"))
                    } else {
                        emptyList()
                    },
                    motif = MotifGlyph.PAINT,
                    fieldRole = com.softdread.widgets.domain.model.ColourRole.SLATE,
                    contentDescription = "d",
                ))
        }
        save("motif_toaster_joke_dark", WidgetType.DAILY_JOKE, WidgetBreakpoint.STANDARD, true,
            TileContent(
                label = "daily joke",
                voice = "The toaster has one setting: legal dispute.",
                chips = listOf(com.softdread.widgets.widgets.common.TileChip("regret: medium")),
                motif = MotifGlyph.TOAST,
                fieldRole = com.softdread.widgets.domain.model.ColourRole.CLAY,
                contentDescription = "d",
            ))
        save("motif_screentime_toast", WidgetType.SCREEN_TIME, WidgetBreakpoint.EXPANDED, false,
            TileContent(
                label = "screen time",
                labelDetail = "33 min",
                heroValue = "4.1",
                subhead = "an aggressively long toaster cycle",
                pill = "You've spent 33 min on-screen today.",
                motif = MotifGlyph.TOAST,
                contentDescription = "d",
            ), widthDp = 500, heightDp = 290)
        save("motif_screentime_film_compact", WidgetType.SCREEN_TIME, WidgetBreakpoint.COMPACT, false,
            SampleData.content(WidgetType.SCREEN_TIME, WidgetBreakpoint.COMPACT))
        save("motif_moon_joke_dark", WidgetType.DAILY_JOKE, WidgetBreakpoint.EXPANDED, true,
            TileContent(
                label = "daily joke",
                labelDetail = "26 aug",
                voice = "The moon has not replied to my email. Typical management.",
                chips = listOf(com.softdread.widgets.widgets.common.TileChip("absurdity index: 94%")),
                motif = MotifGlyph.MOON,
                fieldRole = com.softdread.widgets.domain.model.ColourRole.NIGHT,
                contentDescription = "d",
            ))

        // A contact sheet of the full glyph vocabulary: every pictogram on a
        // joke tile, so a human pass can judge the whole family at once.
        MotifGlyph.entries.forEach { glyph ->
            save("glyph_${glyph.name.lowercase()}", WidgetType.DAILY_JOKE, WidgetBreakpoint.COMPACT, false,
                TileContent(
                    label = glyph.name.lowercase(),
                    voice = "the ${glyph.name.lowercase()} motif",
                    motif = glyph,
                    contentDescription = "d",
                ))
        }
    }
}
