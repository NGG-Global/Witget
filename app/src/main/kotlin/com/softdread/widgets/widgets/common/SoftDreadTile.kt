package com.softdread.widgets.widgets.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import com.softdread.widgets.design.SoftDreadShape
import com.softdread.widgets.design.SoftDreadTiles
import com.softdread.widgets.design.TileColours
import com.softdread.widgets.domain.model.ColourRole
import com.softdread.widgets.domain.model.WidgetBreakpoint
import java.util.Locale

/**
 * The one tile renderer every widget in the pack draws through.
 *
 * It owns the design sheet's non-negotiable rules so no individual widget can
 * break them: one permanent colour per widget, exactly one cropped circle, a
 * copy pill at 4x4 only, lowercase everywhere except micro-labels, no shadows,
 * no gradients, full-bleed field with no inner card.
 *
 * Layout adapts by breakpoint rather than scaling: the compact tile drops
 * secondary metrics and subheads, the standard tile puts the leading visual
 * beside the copy, and only the expanded tile earns a pill and chips.
 */
@Composable
fun SoftDreadTile(
    role: ColourRole,
    breakpoint: WidgetBreakpoint,
    colours: TileColours,
    content: TileContent,
    onClick: Action? = null,
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    val widthPx = (size.width.value * density).toInt().coerceAtLeast(1)
    val heightPx = (size.height.value * density).toInt().coerceAtLeast(1)
    val radius: Dp = breakpoint.cornerRadiusDp.dp

    val satelliteColours = content.satelliteRole
        ?.let { colours.copy(satellite = SoftDreadTiles.colours(it).surface) }
        ?: colours

    val background = TileArt.background(
        widthPx = widthPx,
        heightPx = heightPx,
        cornerRadiusPx = radius.value * density,
        surface = colours.surface,
        circle = TileGeometry.circle(role, breakpoint, satelliteColours),
        satellite = TileGeometry.satellite(role, breakpoint, satelliteColours),
        insetStroke = if (colours.needsInsetStroke) colours.onSurface.copy(alpha = 0.16f) else null,
    )

    var modifier = GlanceModifier
        .fillMaxSize()
        .cornerRadius(radius)
        .background(ImageProvider(background), contentScale = ContentScale.FillBounds)
        .semantics { contentDescription = content.contentDescription }
    if (onClick != null) modifier = modifier.clickable(onClick)

    Box(modifier = modifier) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(
                    horizontal = breakpoint.paddingHorizontalDp.dp,
                    vertical = breakpoint.paddingVerticalDp.dp,
                ),
        ) {
            when (breakpoint) {
                WidgetBreakpoint.TINY -> TinyLayout(content, colours, breakpoint)
                WidgetBreakpoint.COMPACT -> CompactLayout(content, colours, breakpoint, density)
                WidgetBreakpoint.STANDARD, WidgetBreakpoint.WIDE ->
                    StandardLayout(content, colours, breakpoint, density)
                WidgetBreakpoint.EXPANDED -> ExpandedLayout(content, colours, breakpoint, density, context)
            }
        }
    }
}

/** 2x1: "LABEL + VALUE ONLY, RADIUS 28, NO CIRCLE" — the sheet's extension note. */
@Composable
private fun TinyLayout(content: TileContent, colours: TileColours, breakpoint: WidgetBreakpoint) {
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        LabelRow(content, colours, breakpoint)
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = content.heroValue ?: content.voice.orEmpty(),
            style = GlanceType.hero(breakpoint, colours.onSurface),
            maxLines = 1,
        )
    }
}

@Composable
private fun CompactLayout(
    content: TileContent,
    colours: TileColours,
    breakpoint: WidgetBreakpoint,
    density: Float,
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        LabelRow(content, colours, breakpoint)
        Spacer(GlanceModifier.defaultWeight())

        val leading = content.leading
        if (leading != null && content.heroValue != null) {
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                LeadingVisualView(leading, colours, sizeDp = 52.dp, density = density)
                Spacer(GlanceModifier.width(10.dp))
                Text(
                    text = content.heroValue,
                    style = GlanceType.hero(breakpoint, colours.onSurface),
                    maxLines = 1,
                )
            }
        } else if (content.heroValue != null) {
            Text(
                text = content.heroValue,
                style = GlanceType.hero(breakpoint, colours.onSurface),
                maxLines = 1,
            )
        } else if (leading != null) {
            LeadingVisualView(leading, colours, sizeDp = 52.dp, density = density)
        }

        content.voice?.let {
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = it,
                style = GlanceType.voice(breakpoint, colours.onSurfaceMuted),
                maxLines = if (content.heroValue == null) 4 else 3,
            )
        }
        // The sheet shows at most one metadata chip at 2x2.
        if (content.chips.isNotEmpty()) {
            Spacer(GlanceModifier.height(7.dp))
            ChipView(content.chips.first(), colours, density)
        }
        content.callToAction?.let {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = it.uppercase(Locale.getDefault()),
                style = GlanceType.CallToAction(colours.onSurface.copy(alpha = 0.6f)),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StandardLayout(
    content: TileContent,
    colours: TileColours,
    breakpoint: WidgetBreakpoint,
    density: Float,
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        val leading = content.leading
        if (leading is LeadingVisual.Numeral) {
            Text(
                text = leading.text,
                style = GlanceType.heroNumeral(breakpoint, colours.onSurface),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(18.dp))
        } else if (leading != null) {
            LeadingVisualView(leading, colours, sizeDp = 82.dp, density = density)
            Spacer(GlanceModifier.width(18.dp))
        }

        Column(modifier = GlanceModifier.defaultWeight()) {
            LabelRow(content, colours, breakpoint)
            if (content.heroValue != null) {
                Spacer(GlanceModifier.height(7.dp))
                Row(verticalAlignment = Alignment.Vertical.Bottom) {
                    Text(
                        text = content.heroValue,
                        style = GlanceType.hero(breakpoint, colours.onSurface),
                        maxLines = 1,
                    )
                    content.heroSuffix?.let {
                        Spacer(GlanceModifier.width(6.dp))
                        Text(it, style = GlanceType.metric(breakpoint, colours.onSurfaceMuted), maxLines = 1)
                    }
                    content.metric?.let {
                        Spacer(GlanceModifier.width(8.dp))
                        Text(it, style = GlanceType.metric(breakpoint, colours.onSurfaceMuted), maxLines = 1)
                    }
                }
            }
            if (content.bars.isNotEmpty()) {
                Spacer(GlanceModifier.height(8.dp))
                content.bars.forEach { bar ->
                    BarRow(bar, colours, density)
                    Spacer(GlanceModifier.height(7.dp))
                }
            }
            content.voice?.let {
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = it,
                    style = if (content.heroValue == null && content.bars.isEmpty()) {
                        GlanceType.statement(breakpoint, colours.onSurface)
                    } else {
                        GlanceType.voice(breakpoint, colours.onSurface)
                    },
                    maxLines = if (content.heroValue == null) 4 else 2,
                )
            }
            if (content.chips.isNotEmpty() && breakpoint.showsSecondaryMetadata) {
                Spacer(GlanceModifier.height(8.dp))
                ChipRow(content.chips, colours, density)
            }
            content.callToAction?.let {
                Spacer(GlanceModifier.height(8.dp))
                Text(
                    text = it.uppercase(Locale.getDefault()),
                    style = GlanceType.CallToAction(colours.onSurface.copy(alpha = 0.6f)),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ExpandedLayout(
    content: TileContent,
    colours: TileColours,
    breakpoint: WidgetBreakpoint,
    density: Float,
    context: Context,
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        LabelRow(content, colours, breakpoint)

        val leading = content.leading
        if (leading != null && leading !is LeadingVisual.Numeral) {
            Spacer(GlanceModifier.height(18.dp))
            LeadingVisualView(
                leading,
                colours,
                sizeDp = if (leading is LeadingVisual.Ring) 150.dp else 104.dp,
                density = density,
                breakpoint = breakpoint,
            )
        }

        if (content.bars.isNotEmpty()) {
            Spacer(GlanceModifier.height(20.dp))
            content.bars.forEach { bar ->
                LargeBarRow(bar, colours, breakpoint, density)
                Spacer(GlanceModifier.height(16.dp))
            }
        }

        Spacer(GlanceModifier.defaultWeight())

        if (content.strip.isNotEmpty()) {
            Image(
                provider = ImageProvider(
                    TileArt.strip(
                        widthPx = (320 * density).toInt(),
                        heightPx = (5 * density).toInt(),
                        values = content.strip,
                        colour = colours.onSurface,
                        gapPx = 10f * density,
                    ),
                ),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxWidth().height(5.dp),
            )
            Spacer(GlanceModifier.height(14.dp))
        }

        content.heroValue?.let { hero ->
            Row(verticalAlignment = Alignment.Vertical.Bottom) {
                Text(
                    text = hero,
                    style = GlanceType.hero(breakpoint, colours.onSurface),
                    maxLines = 1,
                )
                content.heroSuffix?.let {
                    Spacer(GlanceModifier.width(8.dp))
                    Text(it, style = GlanceType.subhead(breakpoint, colours.onSurfaceMuted), maxLines = 1)
                }
                content.metric?.let {
                    Spacer(GlanceModifier.width(10.dp))
                    Text(it, style = GlanceType.metric(breakpoint, colours.onSurfaceMuted), maxLines = 2)
                }
            }
        }

        content.subhead?.let {
            Spacer(GlanceModifier.height(6.dp))
            Text(it, style = GlanceType.subhead(breakpoint, colours.onSurface), maxLines = 2)
        }

        if (content.heroValue == null && content.subhead == null) {
            content.voice?.let {
                Text(it, style = GlanceType.statement(breakpoint, colours.onSurface), maxLines = 6)
            }
        }

        // "Copy sits in a cream pill on 4x4 only." — design sheet, rule 03.
        content.pill?.let { pillText ->
            Spacer(GlanceModifier.height(16.dp))
            PillView(pillText, colours, breakpoint, density, context)
        }

        if (content.chips.isNotEmpty()) {
            Spacer(GlanceModifier.height(14.dp))
            ChipRow(content.chips, colours, density)
        }

        content.callToAction?.let {
            Spacer(GlanceModifier.height(14.dp))
            Text(
                text = it.uppercase(Locale.getDefault()),
                style = GlanceType.CallToAction(colours.onSurface.copy(alpha = 0.6f)),
                maxLines = 1,
            )
        }
    }
}

/**
 * The micro-label row. [TileContent.labelDetail] rides here at every size, which
 * is how tiles whose hero is an interpretation — Screen Time's ratio, for
 * instance — still surface the literal metric the Content Bible insists on
 * keeping visible.
 */
@Composable
private fun LabelRow(content: TileContent, colours: TileColours, breakpoint: WidgetBreakpoint) {
    if (content.labelDetail == null) {
        MicroLabel(content.label, colours, breakpoint)
        return
    }
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.Bottom) {
        Box(modifier = GlanceModifier.defaultWeight()) {
            MicroLabel(content.label, colours, breakpoint)
        }
        Text(
            text = content.labelDetail.uppercase(Locale.getDefault()),
            style = GlanceType.microLabel(breakpoint, colours.label),
            maxLines = 1,
        )
    }
}

@Composable
private fun MicroLabel(label: String, colours: TileColours, breakpoint: WidgetBreakpoint) {
    Text(
        // "Lowercase everywhere except micro-labels." — design sheet, rule 04.
        text = label.uppercase(Locale.getDefault()),
        style = GlanceType.microLabel(breakpoint, colours.label),
        maxLines = 1,
    )
}

@Composable
private fun LeadingVisualView(
    visual: LeadingVisual,
    colours: TileColours,
    sizeDp: Dp,
    density: Float,
    breakpoint: WidgetBreakpoint = WidgetBreakpoint.COMPACT,
) {
    val sizePx = (sizeDp.value * density).toInt()
    when (visual) {
        is LeadingVisual.Ring -> Box(
            modifier = GlanceModifier.size(sizeDp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(
                    TileArt.ring(
                        sizePx = sizePx,
                        fraction = visual.fraction,
                        trackColour = colours.onSurface.copy(alpha = 0.26f),
                        fillColour = visual.fillColour ?: colours.circle,
                        centreColour = if (visual.centreLabel != null) colours.surface else null,
                    ),
                ),
                contentDescription = null,
                modifier = GlanceModifier.size(sizeDp),
            )
            if (visual.centreLabel != null) {
                Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                    Text(
                        text = visual.centreLabel,
                        style = GlanceType.hero(breakpoint, colours.onSurface),
                        maxLines = 1,
                    )
                    visual.centreDetail?.let {
                        Text(
                            text = it,
                            style = GlanceType.metric(breakpoint, colours.onSurfaceMuted),
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        is LeadingVisual.Disc -> Box(
            modifier = GlanceModifier.size(sizeDp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(TileArt.disc(sizePx, visual.colour)),
                contentDescription = null,
                modifier = GlanceModifier.size(sizeDp),
            )
            visual.label?.let {
                Text(
                    text = it,
                    style = GlanceType.hero(breakpoint, colours.surface),
                    maxLines = 1,
                )
            }
        }

        is LeadingVisual.Dots -> Image(
            provider = ImageProvider(
                TileArt.dots(
                    widthPx = sizePx,
                    heightPx = (sizePx * 0.55f).toInt(),
                    filled = visual.filled,
                    total = visual.total,
                    dotColour = colours.onSurface,
                    emptyColour = colours.onSurface.copy(alpha = 0.2f),
                ),
            ),
            contentDescription = null,
            modifier = GlanceModifier.width(sizeDp).height(sizeDp * 0.55f),
        )

        is LeadingVisual.Numeral -> Text(
            text = visual.text,
            style = GlanceType.heroNumeral(breakpoint, colours.onSurface),
            maxLines = 1,
        )
    }
}

@Composable
private fun BarRow(bar: TileBar, colours: TileColours, density: Float) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = bar.label,
            style = GlanceType.chip(colours.onSurfaceMuted),
            maxLines = 1,
            modifier = GlanceModifier.width(52.dp),
        )
        Image(
            provider = ImageProvider(
                TileArt.bar(
                    widthPx = (200 * density).toInt(),
                    heightPx = (9 * density).toInt(),
                    fraction = bar.fraction,
                    trackColour = colours.onSurface.copy(alpha = 0.2f),
                    fillColour = SoftDreadTiles.colours(bar.colourRole).surface,
                ),
            ),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.defaultWeight().height(9.dp),
        )
        Text(
            text = bar.valueText,
            style = GlanceType.chip(colours.onSurface),
            maxLines = 1,
            modifier = GlanceModifier.width(42.dp),
        )
    }
}

@Composable
private fun LargeBarRow(
    bar: TileBar,
    colours: TileColours,
    breakpoint: WidgetBreakpoint,
    density: Float,
) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.Bottom) {
            Text(
                text = bar.label,
                style = GlanceType.metric(breakpoint, colours.onSurfaceMuted),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = bar.valueText,
                style = GlanceType.subhead(breakpoint, colours.onSurface),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(7.dp))
        Image(
            provider = ImageProvider(
                TileArt.bar(
                    widthPx = (320 * density).toInt(),
                    heightPx = (14 * density).toInt(),
                    fraction = bar.fraction,
                    trackColour = colours.onSurface.copy(alpha = 0.18f),
                    fillColour = SoftDreadTiles.colours(bar.colourRole).surface,
                ),
            ),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxWidth().height(14.dp),
        )
    }
}

@Composable
private fun ChipRow(chips: List<TileChip>, colours: TileColours, density: Float) {
    Row(modifier = GlanceModifier.wrapContentHeight()) {
        chips.take(2).forEachIndexed { index, chip ->
            if (index > 0) Spacer(GlanceModifier.width(7.dp))
            ChipView(chip, colours, density)
        }
    }
}

@Composable
private fun ChipView(chip: TileChip, colours: TileColours, density: Float) {
    val background = if (chip.emphasised) colours.contrastChip else colours.pillBackground
    val text = if (chip.emphasised) colours.pillBackground else colours.pillText
    Box(
        modifier = GlanceModifier
            .cornerRadius(SoftDreadShape.ChipRadius)
            .background(
                ImageProvider(
                    TileArt.pill(
                        widthPx = (160 * density).toInt(),
                        heightPx = (28 * density).toInt(),
                        cornerRadiusPx = SoftDreadShape.ChipRadius.value * density,
                        colour = background,
                    ),
                ),
                contentScale = ContentScale.FillBounds,
            )
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(chip.text, style = GlanceType.chip(text), maxLines = 1)
    }
}

@Composable
private fun PillView(
    text: String,
    colours: TileColours,
    breakpoint: WidgetBreakpoint,
    density: Float,
    context: Context,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(SoftDreadShape.PillRadius)
            .background(
                ImageProvider(
                    TileArt.pill(
                        widthPx = (320 * density).toInt(),
                        heightPx = (64 * density).toInt(),
                        cornerRadiusPx = SoftDreadShape.PillRadius.value * density,
                        colour = colours.pillBackground,
                    ),
                ),
                contentScale = ContentScale.FillBounds,
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(text, style = GlanceType.pill(breakpoint, colours.pillText), maxLines = 3)
    }
}

/** A chip colour that stands out against the tile's own pill background. */
private val TileColours.contrastChip: Color
    get() = SoftDreadTiles.colours(ColourRole.CLAY).surface
