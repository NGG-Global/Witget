package com.softdread.widgets.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.softdread.widgets.design.Baloo
import com.softdread.widgets.design.SoftDreadTheme

/**
 * The witget wordmark, drawn live: Baloo 700 ink with the logo's coral dot
 * replacing the i's tittle and the amber counter filling the g's bowl.
 *
 * The anchor constants are measured, not styled: a calibration test renders the
 * face at 400px, pixel-scans the tittle and the g's enclosed counter, and these
 * ratios are its output (tittle centre 0.546 of the i's advance, 0.624em above
 * the baseline; counter centre 0.507 of the g's advance, 0.2125em above). The
 * coral is drawn over the tittle at 1.6x its radius so no ink fringes; the
 * amber is drawn *under* the text so the glyph's own edge crops it — exactly
 * how the logo builds the effect. Rendering from the live font keeps the mark
 * crisp at any size and lets it participate in text layout.
 */
@Composable
fun WitgetWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 34.sp,
    colour: Color? = null,
) {
    val chrome = SoftDreadTheme.chrome
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val sizePx = with(LocalDensity.current) { fontSize.toPx() }

    Text(
        text = "witget",
        style = TextStyle(
            fontFamily = Baloo,
            fontWeight = FontWeight.W700,
            fontSize = fontSize,
        ),
        color = colour ?: chrome.onSurface,
        maxLines = 1,
        onTextLayout = { layoutResult = it },
        modifier = modifier
            .drawBehind {
                val layout = layoutResult ?: return@drawBehind
                val baseline = layout.getLineBaseline(0)
                val gStart = layout.getHorizontalPosition(3, usePrimaryDirection = true)
                val gEnd = layout.getHorizontalPosition(4, usePrimaryDirection = true)
                drawCircle(
                    color = Amber,
                    radius = sizePx * 0.1265f,
                    center = Offset(gStart + (gEnd - gStart) * 0.507f, baseline - sizePx * 0.2125f),
                )
            }
            .drawWithContent {
                drawContent()
                val layout = layoutResult ?: return@drawWithContent
                val baseline = layout.getLineBaseline(0)
                val iStart = layout.getHorizontalPosition(1, usePrimaryDirection = true)
                val iEnd = layout.getHorizontalPosition(2, usePrimaryDirection = true)
                drawCircle(
                    color = Coral,
                    radius = sizePx * 0.104f,
                    center = Offset(iStart + (iEnd - iStart) * 0.546f, baseline - sizePx * 0.624f),
                )
            },
    )
}

// The logo's own accent values.
private val Coral = Color(0xFFCC6848)
private val Amber = Color(0xFFF0B058)
