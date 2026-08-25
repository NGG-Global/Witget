package com.softdread.widgets.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softdread.widgets.R
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import com.softdread.widgets.design.CircleField
import com.softdread.widgets.design.SoftDreadSpacing
import com.softdread.widgets.design.entrance
import com.softdread.widgets.design.pressScale
import com.softdread.widgets.design.SoftDreadTheme
import com.softdread.widgets.design.SoftDreadTiles
import com.softdread.widgets.design.SoftDreadType
import com.softdread.widgets.domain.model.ColourRole
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.ui.GalleryCard
import com.softdread.widgets.ui.SoftDreadViewModel
import com.softdread.widgets.ui.components.Masthead
import com.softdread.widgets.ui.components.PreviewTile
import com.softdread.widgets.ui.components.Rule
import com.softdread.widgets.ui.components.SectionLabel
import com.softdread.widgets.ui.components.SpecLine
import com.softdread.widgets.ui.components.StatusBadge

/**
 * The widget gallery.
 *
 * Laid out as the design sheet's own widget matrix rather than as a list of
 * Material cards: an index and a name on the left, the live tile on the right,
 * and a hard rule between entries. Every tile is rendered through the same
 * pipeline the home screen uses, so browsing the pack is an honest preview of
 * what each widget will say — including "needs setup", which is worth knowing
 * before you place it rather than after.
 */
@Composable
fun GalleryScreen(
    viewModel: SoftDreadViewModel,
    onOpenWidget: (WidgetType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val chrome = SoftDreadTheme.chrome

    Box(modifier = modifier.fillMaxSize()) {
        CircleField(isDark = chrome.isDark, dim = 0.6f)
        GalleryList(cards = cards, chrome = chrome, onOpenWidget = onOpenWidget)
    }
}

@Composable
private fun GalleryList(
    cards: List<GalleryCard>,
    chrome: com.softdread.widgets.design.SoftDreadChrome,
    onOpenWidget: (WidgetType) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SoftDreadSpacing.XLarge,
            end = SoftDreadSpacing.XLarge,
            top = SoftDreadSpacing.XLarge,
            bottom = SoftDreadSpacing.XXLarge,
        ),
    ) {
        item {
            Box(Modifier.entrance(0)) {
                Masthead(
                    title = stringResource(R.string.gallery_title),
                    subtitle = stringResource(R.string.gallery_subtitle),
                )
            }
            Spacer(Modifier.height(SoftDreadSpacing.XLarge))
            Box(Modifier.entrance(1)) {
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.headlineMedium,
                    color = chrome.onSurface,
                )
            }
            Spacer(Modifier.height(SoftDreadSpacing.XLarge))

            // The sheet opens with its palette; so does the pack.
            Box(Modifier.entrance(2)) { SwatchStrip(isDark = chrome.isDark) }
            Spacer(Modifier.height(SoftDreadSpacing.XLarge))

            Box(Modifier.entrance(3)) {
                SectionLabel(stringResource(R.string.gallery_section_widgets))
            }
            Spacer(Modifier.height(SoftDreadSpacing.Medium))
        }

        itemsIndexed(cards, key = { _, card -> card.entry.type.id }) { index, card ->
            Box(Modifier.entrance(index + 4)) {
                WidgetMatrixRow(card = card, onClick = { onOpenWidget(card.entry.type) })
            }
        }
    }
}

/** One row of the sheet's "palette · one owner per swatch". */
@Composable
private fun SwatchStrip(isDark: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ColourRole.entries.forEach { role ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SoftDreadTiles.colours(role, dark = isDark).surface),
            )
        }
    }
}

@Composable
private fun WidgetMatrixRow(card: GalleryCard, onClick: () -> Unit) {
    val chrome = SoftDreadTheme.chrome
    val type = card.entry.type
    val containerWidth = LocalWindowInfo.current.containerSize.width
    val isWide = with(LocalDensity.current) { containerWidth.toDp() } >= 600.dp
    val tileWidth = if (isWide) 172.dp else 136.dp

    val interactionSource = remember { MutableInteractionSource() }
    val scale = pressScale(interactionSource)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Rule()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = SoftDreadSpacing.Large),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = SoftDreadSpacing.Large)) {
                Text(
                    text = stringResource(card.entry.nameRes).lowercase(java.util.Locale.getDefault()),
                    style = MaterialTheme.typography.titleLarge,
                    color = chrome.onSurface,
                )
                Spacer(Modifier.height(SoftDreadSpacing.XSmall))

                // The sheet annotates every widget with its index and colour.
                SpecLine("%02d · %s".format(type.ordinal + 1, type.colourRole.name))

                Spacer(Modifier.height(SoftDreadSpacing.Medium))
                Text(
                    text = stringResource(card.entry.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = chrome.secondaryType,
                )
                Spacer(Modifier.height(SoftDreadSpacing.Medium))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(
                        text = if (card.preview?.needsSetup == true) {
                            stringResource(R.string.gallery_needs_setup)
                        } else {
                            stringResource(R.string.gallery_ready)
                        },
                        positive = card.preview?.needsSetup != true,
                    )
                    if (card.placedCount > 0) {
                        Spacer(Modifier.width(SoftDreadSpacing.Small))
                        SpecLine(
                            pluralStringResource(
                                R.plurals.detail_instances_count,
                                card.placedCount,
                                card.placedCount,
                            ),
                        )
                    }
                }
                if (card.preview?.isSample == true) {
                    Spacer(Modifier.height(SoftDreadSpacing.Small))
                    SpecLine(stringResource(R.string.gallery_sample_label))
                }
            }

            card.preview?.let { preview ->
                PreviewTile(
                    role = type.colourRole,
                    breakpoint = WidgetBreakpoint.COMPACT,
                    content = preview.content,
                    isDark = chrome.isDark,
                    modifier = Modifier.width(tileWidth),
                )
            } ?: Box(Modifier.size(tileWidth))
        }
    }
}
