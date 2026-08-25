package com.softdread.widgets.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Witget mark, drawn from the logo: a soft squircle quartered into the
 * pack's fields — sage, cream, slate, amber — with the clay circle overlapping
 * the top-right corner and a speech bubble carrying the "w".
 *
 * Colours here are the logo's own, sampled from the brand artwork rather than
 * taken from the widget palette: this is the one place the app renders the
 * brand asset itself, so it matches the logo exactly in both themes, the way
 * an app icon does.
 */
@Composable
fun WitgetMark(size: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size.dp)) {
        val s = this.size.minDimension
        val corner = CornerRadius(s * 0.29f)
        val tile = Path().apply {
            addRoundRect(RoundRect(0f, 0f, s, s, corner, corner))
        }
        clipPath(tile) {
            // Base panel and the three colour quadrants.
            drawRect(PanelCream)
            drawRect(Sage, topLeft = Offset.Zero, size = Size(s * 0.61f, s * 0.38f))
            drawRect(Slate, topLeft = Offset(0f, s * 0.58f), size = Size(s * 0.46f, s * 0.42f))
            drawRect(Amber, topLeft = Offset(s * 0.46f, s * 0.58f), size = Size(s * 0.54f, s * 0.42f))

            // The panel's two "text widget" lines, only when there is room.
            if (s >= smallDetailCutoffPx()) {
                val lineHeight = s * 0.045f
                drawRoundRect(
                    Amber,
                    topLeft = Offset(s * 0.09f, s * 0.44f),
                    size = Size(s * 0.24f, lineHeight),
                    cornerRadius = CornerRadius(lineHeight / 2f),
                )
                drawRoundRect(
                    Amber.copy(alpha = 0.6f),
                    topLeft = Offset(s * 0.09f, s * 0.51f),
                    size = Size(s * 0.17f, lineHeight),
                    cornerRadius = CornerRadius(lineHeight / 2f),
                )
            }

            // The clay circle, cropped by the top and right edges like the logo.
            drawCircle(Clay, radius = s * 0.34f, center = Offset(s * 0.68f, s * 0.34f))
        }

        // Speech bubble with its tail, riding the clay circle.
        val bubbleCentre = Offset(s * 0.68f, s * 0.34f)
        val bubbleRadius = s * 0.195f
        drawCircle(Bubble, radius = bubbleRadius, center = bubbleCentre)
        val tail = Path().apply {
            moveTo(s * 0.575f, s * 0.44f)
            lineTo(s * 0.50f, s * 0.545f)
            lineTo(s * 0.615f, s * 0.49f)
            close()
        }
        drawPath(tail, Bubble)

        // The rounded "w", stroked with round caps like the wordmark.
        if (s >= smallDetailCutoffPx()) {
            val w = Path().apply {
                moveTo(s * 0.605f, s * 0.285f)
                lineTo(s * 0.636f, s * 0.395f)
                lineTo(s * 0.68f, s * 0.31f)
                lineTo(s * 0.724f, s * 0.395f)
                lineTo(s * 0.755f, s * 0.285f)
            }
            drawPath(
                w,
                Ink,
                style = Stroke(width = s * 0.052f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.smallDetailCutoffPx(): Float =
    40.dp.toPx()

// Sampled from the logo artwork.
private val Clay = Color(0xFFCC6848)
private val Sage = Color(0xFF94A082)
private val Slate = Color(0xFF4C5C71)
private val Amber = Color(0xFFF0B058)
private val PanelCream = Color(0xFFF8E3C8)
private val Bubble = Color(0xFFFFFBF2)
private val Ink = Color(0xFF3B2521)
