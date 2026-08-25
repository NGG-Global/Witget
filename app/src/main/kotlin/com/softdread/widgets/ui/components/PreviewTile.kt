package com.softdread.widgets.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softdread.widgets.design.SoftDreadShape
import com.softdread.widgets.design.SoftDreadTiles
import com.softdread.widgets.design.SoftDreadType
import com.softdread.widgets.design.TileColours
import com.softdread.widgets.domain.model.ColourRole
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.widgets.common.CircleAnchor
import com.softdread.widgets.widgets.common.LeadingVisual
import com.softdread.widgets.widgets.common.TileBar
import com.softdread.widgets.widgets.common.TileChip
import com.softdread.widgets.widgets.common.TileContent
import com.softdread.widgets.widgets.common.TileGeometry
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.min

/**
 * The in-app tile renderer.
 *
 * It draws the same [TileContent] the home-screen widget draws, but in Compose
 * rather than Glance — so the preview carries the real Bricolage Grotesque, real
 * letter-spacing and vector-crisp circles that Glance cannot reproduce. That
 * makes the gallery the truest representation of the design sheet in the
 * product, and the placed widget a faithful approximation of it.
 */
@Composable
fun PreviewTile(
    role: ColourRole,
    breakpoint: WidgetBreakpoint,
    content: TileContent,
    modifier: Modifier = Modifier,
    isDark: Boolean = false,
) {
    val baseColours = SoftDreadTiles.colours(role, dark = isDark)
    val colours = content.satelliteRole
        ?.let { baseColours.copy(satellite = SoftDreadTiles.colours(it, dark = isDark).surface) }
        ?: baseColours
    val circle = TileGeometry.circle(role, breakpoint, colours)
    val satellite = TileGeometry.satellite(role, breakpoint, colours)
    val radius = breakpoint.cornerRadiusDp.dp

    Box(
        modifier = modifier
            .aspectRatio(breakpoint.widthDp.toFloat() / breakpoint.heightDp.toFloat())
            .clip(RoundedCornerShape(radius))
            .background(colours.surface)
            .then(
                if (colours.needsInsetStroke) {
                    Modifier.border(1.dp, colours.hairline, RoundedCornerShape(radius))
                } else {
                    Modifier
                },
            )
            .drawBehind {
                val reference = min(size.width, size.height)
                circle?.let { spec ->
                    val diameter = reference * spec.diameterRatio
                    val overhang = reference * spec.overhangRatio
                    drawCircle(
                        color = spec.colour,
                        radius = diameter / 2f,
                        center = anchorCentre(spec.anchor, size, diameter, overhang),
                    )
                }
                satellite?.let { spec ->
                    drawCircle(
                        color = spec.colour,
                        radius = reference * spec.diameterRatio / 2f,
                        center = Offset(size.width * spec.centreXRatio, size.height * spec.centreYRatio),
                    )
                }
            }
            .padding(
                horizontal = breakpoint.paddingHorizontalDp.dp,
                vertical = breakpoint.paddingVerticalDp.dp,
            )
            .semantics { contentDescription = content.contentDescription },
    ) {
        when (breakpoint) {
            WidgetBreakpoint.EXPANDED, WidgetBreakpoint.HERO -> ExpandedPreview(content, colours, breakpoint)
            WidgetBreakpoint.STANDARD, WidgetBreakpoint.WIDE -> StandardPreview(content, colours, breakpoint)
            else -> CompactPreview(content, colours, breakpoint)
        }
    }
}

/** Mirrors TileRenderer.heroSizeSp so the in-app preview clips exactly when the widget would. */
private fun fittedHero(base: TextStyle, text: String, thresholds: List<Pair<Int, Float>>): TextStyle {
    val size = thresholds.firstOrNull { text.length <= it.first }?.second ?: thresholds.last().second
    return base.copy(fontSize = size.sp, lineHeight = (size * 0.95f).sp)
}

@Composable
private fun CompactPreview(content: TileContent, colours: TileColours, breakpoint: WidgetBreakpoint) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        LabelRow(content, colours)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val ring = content.leading as? LeadingVisual.Ring
            ring?.let {
                RingView(it, colours, 46.dp)
                Spacer(Modifier.width(10.dp))
            }
            if (content.leading is LeadingVisual.EightBall && content.heroValue == null) {
                EightBallView(52.dp)
            }
            content.heroValue?.let {
                val thresholds = if (ring != null) listOf(4 to 32f, 99 to 26f) else listOf(4 to 38f, 7 to 32f, 99 to 26f)
                Text(
                    text = it,
                    style = fittedHero(SoftDreadType.Hero2x2, it, thresholds),
                    color = colours.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column {
            content.voice?.let {
                Text(
                    text = it,
                    style = SoftDreadType.VoiceCompact,
                    color = colours.onSurfaceMuted,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            content.chips.firstOrNull()?.let {
                Spacer(Modifier.height(7.dp))
                ChipView(it, colours)
            }
            content.callToAction?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = it.uppercase(Locale.getDefault()),
                    style = SoftDreadType.MicroLabel,
                    color = colours.callToAction,
                )
            }
        }
    }
}

@Composable
private fun StandardPreview(content: TileContent, colours: TileColours, breakpoint: WidgetBreakpoint) {
    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        when (val leading = content.leading) {
            is LeadingVisual.EightBall -> {
                EightBallView(82.dp)
                Spacer(Modifier.width(18.dp))
            }
            is LeadingVisual.Numeral -> {
                Text(
                    text = leading.text,
                    style = SoftDreadType.Hero4x2.copy(fontSize = SoftDreadType.Hero4x2.fontSize * 1.35f),
                    color = colours.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.width(18.dp))
            }
            is LeadingVisual.Ring -> {
                RingView(leading, colours, 82.dp)
                Spacer(Modifier.width(18.dp))
            }
            is LeadingVisual.Disc -> {
                DiscView(leading, colours, 82.dp)
                Spacer(Modifier.width(18.dp))
            }
            is LeadingVisual.Dots -> {
                DotsView(leading, colours, 82.dp)
                Spacer(Modifier.width(18.dp))
            }
            null -> Unit
        }
        Column(modifier = Modifier.weight(1f)) {
            LabelRow(content, colours)
            content.heroValue?.let {
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = it,
                        style = fittedHero(SoftDreadType.Hero4x2, it, listOf(4 to 46f, 8 to 40f, 99 to 32f)),
                        color = colours.onSurface,
                        maxLines = 1,
                    )
                    content.metric?.let { metric ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = metric,
                            style = SoftDreadType.VoiceCompact,
                            color = colours.onSurfaceMuted,
                            maxLines = 2,
                        )
                    }
                }
            }
            if (content.bars.isNotEmpty()) {
                Spacer(Modifier.height(9.dp))
                content.bars.forEach {
                    BarRow(it, colours)
                    Spacer(Modifier.height(7.dp))
                }
            }
            content.voice?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = it,
                    style = SoftDreadType.Voice,
                    color = colours.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (content.chips.isNotEmpty()) {
                Spacer(Modifier.height(9.dp))
                Row { content.chips.take(2).forEach { ChipView(it, colours); Spacer(Modifier.width(7.dp)) } }
            }
            content.callToAction?.let {
                Spacer(Modifier.height(9.dp))
                Text(
                    text = it.uppercase(Locale.getDefault()),
                    style = SoftDreadType.MicroLabel,
                    color = colours.callToAction,
                )
            }
        }
    }
}

@Composable
private fun ExpandedPreview(content: TileContent, colours: TileColours, breakpoint: WidgetBreakpoint) {
    Column(modifier = Modifier.fillMaxSize()) {
        LabelRow(content, colours)
        when (val leading = content.leading) {
            is LeadingVisual.Ring -> {
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RingView(leading, colours, 150.dp)
                }
            }
            is LeadingVisual.EightBall -> {
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EightBallView(132.dp)
                }
            }
            is LeadingVisual.Disc -> {
                Spacer(Modifier.height(18.dp))
                DiscView(leading, colours, 104.dp)
            }
            is LeadingVisual.Dots -> {
                Spacer(Modifier.height(20.dp))
                DotsView(leading, colours, 150.dp)
            }
            else -> Unit
        }
        if (content.bars.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            content.bars.forEach {
                LargeBarRow(it, colours)
                Spacer(Modifier.height(16.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        if (content.strip.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content.strip.forEach { value ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            // Lowest segment sits at the track tint so an
                            // empty hour is still visible, not invisible.
                            .background(
                                lerp(colours.trackTint, colours.onSurface, value.coerceIn(0f, 1f)),
                            ),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        content.heroValue?.let {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = it,
                    style = fittedHero(SoftDreadType.Hero4x4, it, listOf(4 to 76f, 7 to 64f, 99 to 44f)),
                    color = colours.onSurface,
                    maxLines = 1,
                )
                content.heroSuffix?.let { suffix ->
                    Spacer(Modifier.width(8.dp))
                    Text(suffix, style = SoftDreadType.Subhead, color = colours.onSurfaceMuted)
                }
                content.metric?.let { metric ->
                    Spacer(Modifier.width(10.dp))
                    Text(metric, style = SoftDreadType.VoiceCompact, color = colours.onSurfaceMuted, maxLines = 2)
                }
            }
        }
        content.subhead?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = SoftDreadType.Subhead, color = colours.onSurface, maxLines = 2)
        }
        if (content.heroValue == null && content.subhead == null) {
            content.voice?.let {
                Text(
                    text = it,
                    style = SoftDreadType.Subhead.copy(fontSize = SoftDreadType.Subhead.fontSize * 1.3f),
                    color = colours.onSurface,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        content.pill?.let {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(SoftDreadShape.PillRadius))
                    .background(colours.pillBackground)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            ) {
                Text(it, style = SoftDreadType.VoiceCompact.copy(fontSize = SoftDreadType.Voice.fontSize * 0.88f), color = colours.pillText)
            }
        }
        if (content.chips.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row { content.chips.take(2).forEach { ChipView(it, colours); Spacer(Modifier.width(8.dp)) } }
        }
        content.callToAction?.let {
            Spacer(Modifier.height(14.dp))
            Text(
                text = it.uppercase(Locale.getDefault()),
                style = SoftDreadType.MicroLabel,
                color = colours.callToAction,
            )
        }
    }
}

@Composable
private fun LabelRow(content: TileContent, colours: TileColours) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            text = content.label.uppercase(Locale.getDefault()),
            style = SoftDreadType.MicroLabel,
            color = colours.label,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
        )
        content.labelDetail?.let {
            Spacer(Modifier.weight(1f))
            Text(
                text = it.uppercase(Locale.getDefault()),
                style = SoftDreadType.MicroLabel,
                color = colours.label,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RingView(ring: LeadingVisual.Ring, colours: TileColours, size: Dp) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.fillMaxSize().drawBehind {
                val stroke = this.size.minDimension * 0.26f
                val inset = stroke / 2f
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                drawArc(
                    color = colours.trackTint,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                if (ring.fraction > 0f) {
                    drawArc(
                        color = ring.fillColour ?: colours.circle,
                        startAngle = -90f,
                        sweepAngle = 360f * ring.fraction.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                }
            },
        )
        ring.centreLabel?.let {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(it, style = SoftDreadType.Hero2x2, color = colours.onSurface, maxLines = 1)
                ring.centreDetail?.let { detail ->
                    Text(detail, style = SoftDreadType.VoiceCompact, color = colours.onSurfaceMuted, maxLines = 1)
                }
            }
        }
    }
}

/** The classic object: near-black sphere, white disc, ink 8. */
@Composable
private fun EightBallView(size: Dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(Color(0xFF13161D)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(size * 0.54f).clip(CircleShape).background(Color(0xFFFFFBF2)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "8",
                style = SoftDreadType.Hero2x2.copy(fontSize = (size.value * 0.3f).sp),
                color = Color(0xFF13161D),
            )
        }
    }
}

@Composable
private fun DiscView(disc: LeadingVisual.Disc, colours: TileColours, size: Dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(disc.colour),
        contentAlignment = Alignment.Center,
    ) {
        disc.label?.let {
            Text(it, style = SoftDreadType.Hero2x2, color = colours.surface, maxLines = 1)
        }
    }
}

@Composable
private fun DotsView(dots: LeadingVisual.Dots, colours: TileColours, width: Dp) {
    val columns = min(4, dots.total.coerceAtLeast(1))
    Column(modifier = Modifier.width(width)) {
        (0 until dots.total).chunked(columns).forEach { row ->
            Row {
                row.forEach { index ->
                    Box(
                        modifier = Modifier
                            .padding(3.dp)
                            .size(width / 8)
                            .clip(CircleShape)
                            .background(
                                if (index < dots.filled) colours.onSurface else colours.trackTint,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun BarRow(bar: TileBar, colours: TileColours) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(bar.label, style = SoftDreadType.VoiceCompact, color = colours.onSurfaceMuted, modifier = Modifier.width(52.dp), maxLines = 1)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(9.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(colours.trackTint),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(bar.fraction.coerceIn(0f, 1f))
                    .fillMaxSize()
                    .clip(RoundedCornerShape(5.dp))
                    .background(SoftDreadTiles.colours(bar.colourRole).surface),
            )
        }
        Text(
            text = bar.valueText,
            style = SoftDreadType.VoiceCompact,
            color = colours.onSurface,
            modifier = Modifier.width(42.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun LargeBarRow(bar: TileBar, colours: TileColours) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(bar.label, style = SoftDreadType.Voice, color = colours.onSurfaceMuted, modifier = Modifier.weight(1f))
            Text(bar.valueText, style = SoftDreadType.Subhead, color = colours.onSurface)
        }
        Spacer(Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(colours.trackTint),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(bar.fraction.coerceIn(0f, 1f))
                    .fillMaxSize()
                    .clip(RoundedCornerShape(7.dp))
                    .background(SoftDreadTiles.colours(bar.colourRole).surface),
            )
        }
    }
}

@Composable
private fun ChipView(chip: TileChip, colours: TileColours) {
    val background = if (chip.emphasised) colours.chipEmphasisBg else colours.pillBackground
    val text = if (chip.emphasised) colours.chipEmphasisOn else colours.pillText
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(SoftDreadShape.ChipRadius))
            .background(background)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(chip.text, style = SoftDreadType.MonoLabel, color = text, maxLines = 1)
    }
}

private fun anchorCentre(anchor: CircleAnchor, size: Size, diameter: Float, overhang: Float): Offset {
    val radius = diameter / 2f
    return when (anchor) {
        CircleAnchor.TOP_START -> Offset(radius - overhang, radius - overhang)
        CircleAnchor.TOP_END -> Offset(size.width - radius + overhang, radius - overhang)
        CircleAnchor.BOTTOM_START -> Offset(radius - overhang, size.height - radius + overhang)
        CircleAnchor.BOTTOM_END -> Offset(size.width - radius + overhang, size.height - radius + overhang)
        CircleAnchor.CENTER -> Offset(size.width / 2f, size.height / 2f)
    }
}
