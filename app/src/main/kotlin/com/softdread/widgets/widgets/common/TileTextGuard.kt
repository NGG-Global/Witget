package com.softdread.widgets.widgets.common

/**
 * Keeps tile copy off the tile art.
 *
 * The design sheet's circles are solid cream (or a solid satellite colour), and
 * copy that runs across one loses its contrast entirely — cream voice text over
 * the cream circle was the widget pack's worst real-world readability failure.
 * The renderer knows every shape's rectangle, so before laying text out it asks
 * this guard for the block's safe horizontal span.
 *
 * Pure geometry, no Android types, so the rules are unit-tested on the JVM.
 */
object TileTextGuard {

    /** An opaque shape the text must not cross, in pixels. */
    data class Obstacle(val left: Float, val top: Float, val right: Float, val bottom: Float)

    /** The horizontal span a text block may occupy. */
    data class SafeSpan(val left: Float, val right: Float) {
        val width: Float get() = (right - left).coerceAtLeast(0f)
    }

    /**
     * Computes the safe span for a left-aligned block between [blockTop] and
     * [blockBottom], starting at [contentLeft] and bounded by [contentRight].
     *
     * An obstacle that vertically overlaps the block shrinks it from whichever
     * side the obstacle's centre sits on. Left-side obstacles indent the block;
     * right-side ones cap its width. If an obstacle would leave less than
     * [minWidth], it is ignored for the horizontal pass — the caller is expected
     * to have kept such blocks vertically clear of the art instead.
     */
    fun safeSpan(
        contentLeft: Float,
        contentRight: Float,
        blockTop: Float,
        blockBottom: Float,
        obstacles: List<Obstacle>,
        minWidth: Float,
        gap: Float,
    ): SafeSpan {
        var left = contentLeft
        var right = contentRight
        for (obstacle in obstacles) {
            val overlapsVertically = obstacle.bottom > blockTop && obstacle.top < blockBottom
            if (!overlapsVertically) continue
            val centre = (obstacle.left + obstacle.right) / 2f
            val tileCentre = (contentLeft + contentRight) / 2f
            if (centre >= tileCentre) {
                val candidate = (obstacle.left - gap).coerceAtMost(right)
                if (candidate - left >= minWidth) right = candidate
            } else {
                val candidate = (obstacle.right + gap).coerceAtLeast(left)
                if (right - candidate >= minWidth) left = candidate
            }
        }
        return SafeSpan(left, right)
    }

    /**
     * The circle's bounding rectangle, mirroring TileArt's placement maths so
     * the guard and the drawing can never disagree.
     */
    fun circleObstacle(
        anchor: CircleAnchor,
        diameter: Float,
        overhang: Float,
        width: Float,
        height: Float,
    ): Obstacle {
        val radius = diameter / 2f
        val (cx, cy) = when (anchor) {
            CircleAnchor.TOP_START -> (radius - overhang) to (radius - overhang)
            CircleAnchor.TOP_END -> (width - radius + overhang) to (radius - overhang)
            CircleAnchor.BOTTOM_START -> (radius - overhang) to (height - radius + overhang)
            CircleAnchor.BOTTOM_END -> (width - radius + overhang) to (height - radius + overhang)
            CircleAnchor.CENTER -> (width / 2f) to (height / 2f)
        }
        return Obstacle(cx - radius, cy - radius, cx + radius, cy + radius)
    }
}
