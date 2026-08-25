package com.softdread.widgets.ui.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softdread.widgets.R
import com.softdread.widgets.design.SoftDreadSpacing
import com.softdread.widgets.design.SoftDreadTheme
import com.softdread.widgets.design.SoftDreadType
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.ui.GalleryCard
import com.softdread.widgets.ui.SoftDreadViewModel
import com.softdread.widgets.ui.components.Masthead
import com.softdread.widgets.ui.components.PreviewTile
import com.softdread.widgets.ui.components.SectionLabel
import com.softdread.widgets.ui.components.SoftDreadCard
import com.softdread.widgets.ui.components.StatusBadge

/**
 * The widget gallery.
 *
 * Every card renders a live tile through the same pipeline the home screen uses,
 * so browsing the pack is also an honest preview of what each widget will
 * actually say — including "needs setup", which is a fact worth seeing before
 * placing a widget rather than after.
 */
@Composable
fun GalleryScreen(
    viewModel: SoftDreadViewModel,
    onOpenWidget: (WidgetType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val chrome = SoftDreadTheme.chrome

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SoftDreadSpacing.XLarge,
            end = SoftDreadSpacing.XLarge,
            top = SoftDreadSpacing.XLarge,
            bottom = SoftDreadSpacing.XXLarge,
        ),
        verticalArrangement = Arrangement.spacedBy(SoftDreadSpacing.Medium),
    ) {
        item {
            Masthead(
                title = stringResource(R.string.gallery_title),
                subtitle = stringResource(R.string.gallery_subtitle),
            )
            Spacer(Modifier.height(SoftDreadSpacing.Large))
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.headlineMedium,
                color = chrome.onSurface,
            )
            Spacer(Modifier.height(SoftDreadSpacing.XLarge))
            SectionLabel(stringResource(R.string.gallery_section_widgets))
            Spacer(Modifier.height(SoftDreadSpacing.Small))
        }

        items(cards, key = { it.entry.type.id }) { card ->
            WidgetGalleryCard(card = card, onClick = { onOpenWidget(card.entry.type) })
        }
    }
}

@Composable
private fun WidgetGalleryCard(card: GalleryCard, onClick: () -> Unit) {
    val chrome = SoftDreadTheme.chrome
    // Window size rather than screen size, so a widget card laid out in
    // split-screen or a freeform window sizes to the window it is actually in.
    val density = LocalDensity.current
    val containerWidth = LocalWindowInfo.current.containerSize.width
    val isWide = with(density) { containerWidth.toDp() } >= 600.dp

    SoftDreadCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            card.preview?.let { preview ->
                PreviewTile(
                    role = card.entry.type.colourRole,
                    breakpoint = WidgetBreakpoint.COMPACT,
                    content = preview.content,
                    isDark = chrome.isDark,
                    modifier = Modifier.width(if (isWide) 168.dp else 132.dp),
                )
                Spacer(Modifier.width(SoftDreadSpacing.Large))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(card.entry.nameRes),
                    style = MaterialTheme.typography.titleLarge,
                    color = chrome.onSurface,
                )
                Spacer(Modifier.height(SoftDreadSpacing.XSmall))
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
                        Text(
                            text = pluralStringResource(
                                R.plurals.detail_instances_count,
                                card.placedCount,
                                card.placedCount,
                            ),
                            style = SoftDreadType.Mono,
                            color = chrome.secondaryType,
                        )
                    }
                }
                if (card.preview?.isSample == true) {
                    Spacer(Modifier.height(SoftDreadSpacing.XSmall))
                    Text(
                        text = stringResource(R.string.gallery_sample_label),
                        style = SoftDreadType.Mono,
                        color = chrome.secondaryType,
                    )
                }
            }
        }
    }
}
