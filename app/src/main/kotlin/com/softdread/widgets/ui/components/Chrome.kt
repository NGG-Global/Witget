package com.softdread.widgets.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.softdread.widgets.design.SoftDreadPalette
import com.softdread.widgets.design.SoftDreadShape
import com.softdread.widgets.design.SoftDreadSpacing
import com.softdread.widgets.design.SoftDreadTheme
import com.softdread.widgets.design.SoftDreadType

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

/** The sheet's masthead: the two-circle mark, the wordmark and a mono strapline. */
@Composable
fun Masthead(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppMark(size = 56)
            Spacer(Modifier.width(SoftDreadSpacing.Large))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = SoftDreadTheme.chrome.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = SoftDreadType.Mono,
                    color = SoftDreadTheme.chrome.secondaryType,
                )
            }
        }
        Spacer(Modifier.height(SoftDreadSpacing.Large))
        HorizontalDivider(thickness = 2.dp, color = SoftDreadTheme.chrome.onSurface)
    }
}

/** "Two overlapping circles on cream is the whole mark." */
@Composable
fun AppMark(size: Int, modifier: Modifier = Modifier) {
    val chrome = SoftDreadTheme.chrome
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.29f).dp))
            .background(if (chrome.isDark) SoftDreadPalette.CreamDark else SoftDreadPalette.Cream),
    ) {
        Box(
            modifier = Modifier
                .padding(start = (size * 0.086f).dp, top = (size * 0.115f).dp)
                .size((size * 0.58f).dp)
                .clip(CircleShape)
                .background(if (chrome.isDark) SoftDreadPalette.ClayDark else SoftDreadPalette.Clay),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = (size * 0.105f).dp, bottom = (size * 0.086f).dp)
                .size((size * 0.38f).dp)
                .clip(CircleShape)
                .background(if (chrome.isDark) SoftDreadPalette.SageDark else SoftDreadPalette.Sage),
        )
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

/** A selectable pill. Minimum height keeps it above the 48 dp tap-target floor. */
@Composable
fun ChoicePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    val chrome = SoftDreadTheme.chrome
    val background = when {
        selected -> accent ?: SoftDreadPalette.Clay
        chrome.isDark -> chrome.surface
        else -> SoftDreadPalette.Cream
    }
    val textColour = if (selected) SoftDreadPalette.TypeOnColour else chrome.onSurface
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(SoftDreadShape.ChipRadius),
        color = background,
        border = if (!selected) BorderStroke(1.dp, chrome.hairline) else null,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = SoftDreadSpacing.Large, vertical = SoftDreadSpacing.Medium),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = textColour)
        }
    }
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    val chrome = SoftDreadTheme.chrome
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = SoftDreadSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = chrome.onSurface)
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = chrome.secondaryType)
            }
        }
        Spacer(Modifier.width(SoftDreadSpacing.Medium))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SoftDreadPalette.Cream,
                checkedTrackColor = SoftDreadPalette.Clay,
            ),
        )
    }
}

@Composable
fun StatusBadge(text: String, positive: Boolean, modifier: Modifier = Modifier) {
    val background = if (positive) SoftDreadPalette.Sage else SoftDreadPalette.Amber
    val textColour = if (positive) SoftDreadPalette.TypeOnColour else SoftDreadPalette.LabelOnAmber
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
