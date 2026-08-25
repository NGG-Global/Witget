package com.softdread.widgets.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Responsive breakpoint selection.
 *
 * Launchers rarely report exactly the grid size the design sheet assumes, so
 * selection is threshold-based. These cases cover the sheet's own sizes plus the
 * off-by-a-few-dp values real launchers hand over, and phone, tablet and
 * landscape shapes.
 */
class WidgetBreakpointTest {

    private fun at(width: Float, height: Float) = WidgetBreakpoint.forSize(width, height)

    @Test
    fun `the design sheet's own sizes resolve to their named breakpoints`() {
        assertThat(at(179f, 85f)).isEqualTo(WidgetBreakpoint.TINY)
        assertThat(at(179f, 179f)).isEqualTo(WidgetBreakpoint.COMPACT)
        assertThat(at(368f, 179f)).isEqualTo(WidgetBreakpoint.STANDARD)
        assertThat(at(462f, 179f)).isEqualTo(WidgetBreakpoint.WIDE)
        assertThat(at(368f, 368f)).isEqualTo(WidgetBreakpoint.EXPANDED)
    }

    @Test
    fun `sizes a few dp off still resolve sensibly`() {
        assertThat(at(174f, 176f)).isEqualTo(WidgetBreakpoint.COMPACT)
        assertThat(at(356f, 172f)).isEqualTo(WidgetBreakpoint.STANDARD)
        assertThat(at(360f, 358f)).isEqualTo(WidgetBreakpoint.EXPANDED)
    }

    @Test
    fun `a wide short slot is standard rather than tiny`() {
        assertThat(at(368f, 100f)).isEqualTo(WidgetBreakpoint.STANDARD)
    }

    @Test
    fun `tablet slots resolve to the hero canvas`() {
        // A Pixel Tablet 6x4 cell and larger.
        assertThat(at(600f, 500f)).isEqualTo(WidgetBreakpoint.HERO)
        assertThat(at(520f, 300f)).isEqualTo(WidgetBreakpoint.HERO)
        assertThat(at(800f, 400f)).isEqualTo(WidgetBreakpoint.HERO)
    }

    @Test
    fun `almost-hero slots stay expanded rather than stretching`() {
        assertThat(at(500f, 500f)).isEqualTo(WidgetBreakpoint.EXPANDED)
        assertThat(at(600f, 280f)).isEqualTo(WidgetBreakpoint.EXPANDED)
        // A tablet's larger 2x2 cell is already an expanded-worthy canvas.
        assertThat(at(340f, 340f)).isEqualTo(WidgetBreakpoint.EXPANDED)
    }

    @Test
    fun `a very wide short tablet slot is wide, not hero`() {
        assertThat(at(700f, 180f)).isEqualTo(WidgetBreakpoint.WIDE)
    }

    @Test
    fun `every breakpoint has a usable copy budget and padding`() {
        WidgetBreakpoint.entries.forEach { breakpoint ->
            assertThat(breakpoint.maxResponseChars).isAtLeast(30)
            assertThat(breakpoint.paddingHorizontalDp).isAtLeast(10)
            assertThat(breakpoint.paddingVerticalDp).isAtLeast(10)
            assertThat(breakpoint.cornerRadiusDp).isAtLeast(28)
        }
    }

    @Test
    fun `copy budgets follow the design sheet's limits`() {
        // 2x2 <= 34, 4x2 <= 62, 5x2 <= 78, 4x4 <= 96; hero extends the scale.
        assertThat(WidgetBreakpoint.COMPACT.maxResponseChars).isEqualTo(34)
        assertThat(WidgetBreakpoint.STANDARD.maxResponseChars).isEqualTo(62)
        assertThat(WidgetBreakpoint.WIDE.maxResponseChars).isEqualTo(78)
        assertThat(WidgetBreakpoint.EXPANDED.maxResponseChars).isEqualTo(96)
        assertThat(WidgetBreakpoint.HERO.maxResponseChars).isEqualTo(120)
    }

    @Test
    fun `the 2x1 tile drops the circle and uses the shorter radius`() {
        assertThat(WidgetBreakpoint.TINY.showsCircle).isFalse()
        assertThat(WidgetBreakpoint.TINY.cornerRadiusDp).isEqualTo(28)
        assertThat(WidgetBreakpoint.COMPACT.cornerRadiusDp).isEqualTo(34)
    }

    @Test
    fun `only the large tiles carry a copy pill`() {
        val withPill = WidgetBreakpoint.entries.filter { it.showsPill }
        assertThat(withPill).containsExactly(WidgetBreakpoint.EXPANDED, WidgetBreakpoint.HERO)
    }
}
