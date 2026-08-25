package com.softdread.widgets.widgets.common

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.compose.ui.unit.dp
import com.softdread.widgets.design.TileColours
import com.softdread.widgets.domain.model.ColourRole
import com.softdread.widgets.domain.model.WidgetBreakpoint

/**
 * The Glance host for a tile: one image, one tap target, one spoken description.
 *
 * The tile's entire visual — field, art and text — is produced by
 * [TileRenderer], because RemoteViews cannot render the pack's bundled typeface
 * and Glance text cannot know when it is flowing across a solid circle. What
 * stays in Glance is exactly what Glance is for: the click action and the
 * accessibility contract. [TileContent.contentDescription] carries the real
 * values, so TalkBack reads the tile as data rather than as a picture, and the
 * renderer multiplies every text size by the system font scale.
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
    // Glance provides neither LocalDensity nor LocalResources; display metrics
    // are the only route from the host-reported dp size to renderer pixels.
    @Suppress("LocalContextResourcesRead")
    val density = context.resources.displayMetrics.density
    val widthPx = (size.width.value * density).toInt().coerceAtLeast(1)
    val heightPx = (size.height.value * density).toInt().coerceAtLeast(1)

    val bitmap = TileRenderer.render(
        context = context,
        role = role,
        breakpoint = breakpoint,
        colours = colours,
        content = content,
        widthPx = widthPx,
        heightPx = heightPx,
    )

    var modifier = GlanceModifier
        .fillMaxSize()
        .cornerRadius(breakpoint.cornerRadiusDp.dp)
        .semantics { contentDescription = content.contentDescription }
    if (onClick != null) modifier = modifier.clickable(onClick)

    Box(modifier = modifier) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize(),
        )
    }
}
