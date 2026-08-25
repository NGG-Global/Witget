package com.softdread.widgets.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.softdread.widgets.R
import com.softdread.widgets.design.SoftDreadPalette
import com.softdread.widgets.design.SoftDreadShape
import com.softdread.widgets.design.SoftDreadSpacing
import com.softdread.widgets.design.SoftDreadTheme
import com.softdread.widgets.design.SoftDreadType
import com.softdread.widgets.design.floating
import com.softdread.widgets.design.slowPulse
import androidx.compose.ui.unit.sp

/**
 * App chrome built from the design sheet's own layout language: an eyebrow
 * label in clay above every section, cream cards with generous radii, and a
 * 2px ink rule under the masthead.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(java.util.Locale.getDefault()),
        style = SoftDreadType.SectionLabel,
        color = SoftDreadTheme.chrome.sectionLabel,
        modifier = modifier,
    )
}

/**
 * The masthead. When [wordmark] is set — the gallery — the title renders as the
 * drawn witget wordmark with its coral i-dot and amber g-bowl; elsewhere the
 * title is plain Baloo with the brand's coral full stop.
 */
@Composable
fun Masthead(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    wordmark: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.floating(amplitude = 3.dp, periodMs = 5200)) { WitgetMark(size = 56) }
            Spacer(Modifier.width(SoftDreadSpacing.Large))
            Column {
                if (wordmark) {
                    WitgetWordmark(fontSize = 34.sp)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = title.lowercase(java.util.Locale.getDefault()),
                            style = MaterialTheme.typography.headlineLarge,
                            color = SoftDreadTheme.chrome.onSurface,
                        )
                        Spacer(Modifier.width(3.dp))
                        Dot(
                            colour = SoftDreadTheme.chrome.brandDot,
                            size = 9.dp,
                            modifier = Modifier
                                .padding(bottom = 7.dp)
                                .slowPulse(),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = SoftDreadType.Mono,
                    color = SoftDreadTheme.chrome.secondaryType,
                )
            }
        }
        Spacer(Modifier.height(SoftDreadSpacing.Large))
        // The sheet's masthead rule: 2px ink, hard edge, full bleed.
        Rule(thickness = 2.dp, colour = SoftDreadTheme.chrome.onSurface)
    }
}

@Composable
fun SoftDreadCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(SoftDreadSpacing.Large),
    content: @Composable () -> Unit,
) {
    val chrome = SoftDreadTheme.chrome
    Surface(
        modifier = modifier.fillMaxWidth().then(
            if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier,
        ),
        shape = RoundedCornerShape(SoftDreadShape.CardRadius),
        color = chrome.surface,
        border = if (chrome.isDark) BorderStroke(1.dp, chrome.hairline) else null,
    ) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

@Composable
fun StatusBadge(text: String, positive: Boolean, modifier: Modifier = Modifier) {
    // The badge borrows the sage and amber tiles' resolved colours, so it goes
    // through the same legibility correction as the widgets themselves.
    val chrome = SoftDreadTheme.chrome
    val tile = com.softdread.widgets.design.SoftDreadTiles.colours(
        if (positive) com.softdread.widgets.domain.model.ColourRole.SAGE
        else com.softdread.widgets.domain.model.ColourRole.AMBER,
        chrome.pack,
        chrome.isDark,
    )
    val background = tile.surface
    val textColour = tile.onSurface
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(SoftDreadShape.ChipRadius),
        color = background,
    ) {
        Text(
            text = text,
            style = SoftDreadType.MonoLabel,
            color = textColour,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/**
 * The bottom navigation, rebuilt in the sheet's language.
 *
 * Material's navigation bar carries an icon and a pill indicator; the sheet has
 * neither ("no icons, no illustration"). Each destination is a micro-label with
 * the pack's own circle as its state marker, over a hard ink rule.
 */
@Composable
fun NavTabs(
    destinations: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = SoftDreadTheme.chrome
    Column(modifier = modifier.fillMaxWidth().background(chrome.surface)) {
        Rule(thickness = 2.dp, colour = chrome.onSurface)
        Row(modifier = Modifier.fillMaxWidth()) {
            destinations.forEachIndexed { index, (label, selected) ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp)
                        .clickable(role = Role.Tab) { onSelect(index) }
                        .padding(vertical = SoftDreadSpacing.Large),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val dotSize by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (selected) 10.dp else 8.dp,
                        animationSpec = com.softdread.widgets.design.SoftDreadMotion.pop(),
                        label = "navDot",
                    )
                    val dotColour by androidx.compose.animation.animateColorAsState(
                        targetValue = if (selected) chrome.brandDot else chrome.hairline,
                        animationSpec = com.softdread.widgets.design.SoftDreadMotion.settle(),
                        label = "navDotColour",
                    )
                    val labelColour by androidx.compose.animation.animateColorAsState(
                        targetValue = if (selected) chrome.onSurface else chrome.secondaryType,
                        animationSpec = com.softdread.widgets.design.SoftDreadMotion.settle(),
                        label = "navLabelColour",
                    )
                    Dot(colour = dotColour, size = dotSize)
                    Spacer(Modifier.height(SoftDreadSpacing.Small))
                    Text(
                        text = label.uppercase(java.util.Locale.getDefault()),
                        style = SoftDreadType.SectionLabel,
                        color = labelColour,
                    )
                }
            }
        }
    }
}

/**
 * A screen header. Replaces Material's app bar: a text back affordance, the
 * title set as a headline, and the sheet's rule underneath.
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    val chrome = SoftDreadTheme.chrome
    Column(modifier = modifier.fillMaxWidth()) {
        onBack?.let {
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = it)
                    .padding(vertical = SoftDreadSpacing.Small, horizontal = SoftDreadSpacing.XSmall),
                contentAlignment = Alignment.CenterStart,
            ) {
                // A typographic arrow rather than an icon, per the sheet.
                Text(
                    text = "←  " + stringResource(R.string.common_back).uppercase(java.util.Locale.getDefault()),
                    style = SoftDreadType.SectionLabel,
                    color = chrome.sectionLabel,
                )
            }
            Spacer(Modifier.height(SoftDreadSpacing.Small))
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                text = title.lowercase(java.util.Locale.getDefault()),
                style = MaterialTheme.typography.headlineLarge,
                color = chrome.onSurface,
                modifier = Modifier.weight(1f),
            )
            trailing?.let { SpecLine(it) }
        }
        Spacer(Modifier.height(SoftDreadSpacing.Medium))
        Rule(thickness = 2.dp, colour = chrome.onSurface)
    }
}

/**
 * The expanded-width counterpart to [NavTabs]: a left rail in the same
 * language — the mark on top, destinations as a dot over a micro-label, a hard
 * ink rule along the content edge.
 */
@Composable
fun NavRail(
    destinations: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = SoftDreadTheme.chrome
    Row(modifier = modifier.background(chrome.surface)) {
        Column(
            modifier = Modifier
                .width(112.dp)
                .padding(vertical = SoftDreadSpacing.XLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WitgetMark(size = 48)
            Spacer(Modifier.height(SoftDreadSpacing.XXLarge))
            destinations.forEachIndexed { index, (label, selected) ->
                val dotColour by androidx.compose.animation.animateColorAsState(
                    targetValue = if (selected) chrome.brandDot else chrome.hairline,
                    animationSpec = com.softdread.widgets.design.SoftDreadMotion.settle(),
                    label = "railDot",
                )
                val labelColour by androidx.compose.animation.animateColorAsState(
                    targetValue = if (selected) chrome.onSurface else chrome.secondaryType,
                    animationSpec = com.softdread.widgets.design.SoftDreadMotion.settle(),
                    label = "railLabel",
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable(role = Role.Tab) { onSelect(index) }
                        .padding(vertical = SoftDreadSpacing.Medium),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Dot(colour = dotColour, size = if (selected) 10.dp else 8.dp)
                    Spacer(Modifier.height(SoftDreadSpacing.Small))
                    Text(
                        text = label.uppercase(java.util.Locale.getDefault()),
                        style = SoftDreadType.SectionLabel,
                        color = labelColour,
                    )
                }
            }
        }
        // The rail's content edge carries the sheet's rule, vertically.
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(chrome.onSurface),
        )
    }
}
