package com.softdread.widgets.domain.logic

import com.google.common.truth.Truth.assertThat
import com.softdread.widgets.widgets.common.CircleAnchor
import com.softdread.widgets.widgets.common.TileTextGuard
import org.junit.Test

/**
 * The geometry that keeps copy off the tile art. These are the rules that fix
 * the reported "text over a cream circle is unreadable" failures, so each case
 * mirrors a real tile from the design sheet's matrix.
 */
class TileTextGuardTest {

    private val tile = 537f // a 179dp tile at 3x

    @Test
    fun `a bottom-right circle caps the width of a bottom text block`() {
        // The clay 2x2: solid cream circle anchored bottom-end. The voice line
        // at the bottom must stop before the circle — the sheet itself caps it
        // at max-width 110/179.
        val circle = TileTextGuard.circleObstacle(
            CircleAnchor.BOTTOM_END, diameter = tile * 0.74f, overhang = tile * 0.30f,
            width = tile, height = tile,
        )
        val span = TileTextGuard.safeSpan(
            contentLeft = 45f, contentRight = tile - 45f,
            blockTop = tile - 140f, blockBottom = tile - 40f,
            obstacles = listOf(circle), minWidth = 200f, gap = 24f,
        )
        assertThat(span.left).isEqualTo(45f)
        assertThat(span.right).isLessThan(circle.left)
        assertThat(span.width).isAtLeast(200f)
    }

    @Test
    fun `a top-right satellite pushes the label detail inward`() {
        // The weather 2x2: solid amber satellite at the top-right corner.
        val satellite = TileTextGuard.Obstacle(tile - 180f, -60f, tile + 60f, 180f)
        val span = TileTextGuard.safeSpan(
            contentLeft = 45f, contentRight = tile - 45f,
            blockTop = 40f, blockBottom = 80f,
            obstacles = listOf(satellite), minWidth = 200f, gap = 24f,
        )
        assertThat(span.right).isAtMost(satellite.left - 24f + 0.01f)
    }

    @Test
    fun `a block clear of the circle keeps its full width`() {
        val circle = TileTextGuard.circleObstacle(
            CircleAnchor.BOTTOM_END, diameter = tile * 0.74f, overhang = tile * 0.30f,
            width = tile, height = tile,
        )
        val span = TileTextGuard.safeSpan(
            contentLeft = 45f, contentRight = tile - 45f,
            blockTop = 40f, blockBottom = 90f,
            obstacles = listOf(circle), minWidth = 200f, gap = 24f,
        )
        assertThat(span.left).isEqualTo(45f)
        assertThat(span.right).isEqualTo(tile - 45f)
    }

    @Test
    fun `a left-side obstacle indents rather than capping`() {
        val obstacle = TileTextGuard.Obstacle(-80f, 300f, 160f, 537f)
        val span = TileTextGuard.safeSpan(
            contentLeft = 45f, contentRight = tile - 45f,
            blockTop = 350f, blockBottom = 450f,
            obstacles = listOf(obstacle), minWidth = 200f, gap = 24f,
        )
        assertThat(span.left).isEqualTo(160f + 24f)
        assertThat(span.right).isEqualTo(tile - 45f)
    }

    @Test
    fun `an obstacle that would leave too little width is ignored horizontally`() {
        // The expanded clay tile's 1.63-ratio circle swallows most of the tile;
        // squeezing text into the sliver beside it would be worse than letting
        // the caller keep the block above the circle.
        val obstacle = TileTextGuard.Obstacle(100f, 0f, tile, tile)
        val span = TileTextGuard.safeSpan(
            contentLeft = 45f, contentRight = tile - 45f,
            blockTop = 0f, blockBottom = tile,
            obstacles = listOf(obstacle), minWidth = 300f, gap = 24f,
        )
        assertThat(span.width).isEqualTo(tile - 90f)
    }

    @Test
    fun `circle rectangles match the renderer's anchor maths`() {
        val topStart = TileTextGuard.circleObstacle(CircleAnchor.TOP_START, 200f, 50f, 537f, 537f)
        assertThat(topStart.left).isEqualTo(-50f)
        assertThat(topStart.top).isEqualTo(-50f)
        val centre = TileTextGuard.circleObstacle(CircleAnchor.CENTER, 200f, 0f, 537f, 537f)
        assertThat(centre.left).isEqualTo(537f / 2f - 100f)
    }
}
