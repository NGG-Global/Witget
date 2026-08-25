package com.softdread.widgets.widgets

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.softdread.widgets.design.SoftDreadTiles
import com.softdread.widgets.design.ThemePack
import com.softdread.widgets.domain.model.ColourRole
import com.softdread.widgets.domain.model.Personality
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.ui.preview.SampleData
import com.softdread.widgets.widgets.common.LeadingVisual
import com.softdread.widgets.widgets.common.TileBar
import com.softdread.widgets.widgets.common.TileChip
import com.softdread.widgets.widgets.common.TileContent
import com.softdread.widgets.widgets.common.TileRenderer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders real tiles through the full bitmap pipeline under Robolectric.
 *
 * This cannot judge beauty, but it executes every layout path — StaticLayout,
 * the safe-span guard, bars, rings, chips, the pill, the 8 ball — across all
 * widgets, breakpoints and both modes, so a crash, a negative width or a
 * degenerate layout fails here rather than on someone's launcher.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "xhdpi")
class TileRendererTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun sizeFor(breakpoint: WidgetBreakpoint): Pair<Int, Int> =
        (breakpoint.widthDp * 2) to (breakpoint.heightDp * 2)

    @Test
    fun `every sample tile renders at every breakpoint in both modes`() {
        WidgetType.entries.forEach { type ->
            WidgetBreakpoint.entries.forEach { breakpoint ->
                listOf(false, true).forEach { dark ->
                    val content = SampleData.content(type, breakpoint, Personality.SARCASTIC)
                    val colours = SoftDreadTiles.colours(type.colourRole, ThemePack.CLAY_HOUSE, dark)
                    val (w, h) = sizeFor(breakpoint)
                    val bitmap = TileRenderer.render(context, type.colourRole, breakpoint, colours, content, w, h, densityPx = 2f)
                    assertThat(bitmap.width).isEqualTo(w)
                    assertThat(bitmap.height).isEqualTo(h)
                }
            }
        }
    }

    @Test
    fun `the worst-case content still renders everywhere`() {
        val content = TileContent(
            label = "weather, translated",
            labelDetail = "somewhere very far away",
            heroValue = "100%",
            heroSuffix = "minutes",
            metric = "feels 39\nhigh 36 · low 27",
            subhead = "the extended lord of the rings trilogy",
            voice = "THE RECTANGLE HAS ANNEXED THE DAY. FULL WORKDAY ACHIEVED. ABSOLUTE CINEMA.",
            pill = "Today's usage = 0.7 Lord of the Rings trilogies, which is frankly a commitment.",
            chips = listOf(TileChip("dad approval 91%"), TileChip("regret level: medium", emphasised = true)),
            bars = listOf(
                TileBar("week 35", 0.43f, "43%", ColourRole.SAGE),
                TileBar("august", 0.87f, "87%", ColourRole.EMBER),
                TileBar("the year", 0.65f, "65%", ColourRole.AMBER),
            ),
            strip = listOf(0.1f, 0.4f, 0.9f, 0.7f, 0.2f),
            leading = LeadingVisual.Ring(0.23f, centreLabel = "23%", centreDetail = "~ 2h 10m"),
            callToAction = "tap to ask again",
            contentDescription = "worst case",
        )
        WidgetBreakpoint.entries.forEach { breakpoint ->
            ColourRole.entries.forEach { role ->
                val colours = SoftDreadTiles.colours(role)
                val (w, h) = sizeFor(breakpoint)
                val bitmap = TileRenderer.render(context, role, breakpoint, colours, content, w, h, densityPx = 2f)
                assertThat(bitmap.width).isEqualTo(w)
            }
        }
    }

    @Test
    fun `the 8 ball renders at every size`() {
        val content = TileContent(
            label = "8 ball",
            voice = "Signs point to yes, reluctantly.",
            leading = LeadingVisual.EightBall,
            callToAction = "tap to ask again",
            contentDescription = "8 ball",
        )
        val colours = SoftDreadTiles.colours(ColourRole.NIGHT)
        WidgetBreakpoint.entries.forEach { breakpoint ->
            val (w, h) = sizeFor(breakpoint)
            val bitmap = TileRenderer.render(context, ColourRole.NIGHT, breakpoint, colours, content, w, h, densityPx = 2f)
            assertThat(bitmap.width).isEqualTo(w)
        }
    }

    @Test
    fun `an oversized same-breakpoint tile gains room instead of magnifying`() {
        // A 500x500dp EXPANDED tile at density 2: the unit must stay at the
        // height-fit (which equals the cap here), not stretch to the width fit.
        val content = SampleData.content(WidgetType.SCREEN_TIME, WidgetBreakpoint.EXPANDED)
        val colours = SoftDreadTiles.colours(ColourRole.CLAY)
        val bitmap = TileRenderer.render(
            context, ColourRole.CLAY, WidgetBreakpoint.EXPANDED, colours, content,
            1000, 1000, densityPx = 2f,
        )
        assertThat(bitmap.width).isEqualTo(1000)
    }

    @Test
    fun `hero tiles render for every widget`() {
        WidgetType.entries.forEach { type ->
            val content = SampleData.content(type, WidgetBreakpoint.HERO, Personality.CHAOTIC)
            val colours = SoftDreadTiles.colours(type.colourRole)
            val bitmap = TileRenderer.render(
                context, type.colourRole, WidgetBreakpoint.HERO, colours, content,
                1120, 680, densityPx = 2f,
            )
            assertThat(bitmap.width).isEqualTo(1120)
        }
    }

    @Test
    fun `oversized requests are capped without distorting aspect`() {
        val content = SampleData.content(WidgetType.BATTERY, WidgetBreakpoint.EXPANDED)
        val colours = SoftDreadTiles.colours(ColourRole.SAGE)
        val bitmap = TileRenderer.render(
            context, ColourRole.SAGE, WidgetBreakpoint.EXPANDED, colours, content, 2400, 2400, densityPx = 3f,
        )
        assertThat(bitmap.width).isEqualTo(1600)
        assertThat(bitmap.height).isEqualTo(1600)
    }
}
