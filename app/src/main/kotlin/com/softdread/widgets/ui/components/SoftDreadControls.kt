package com.softdread.widgets.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softdread.widgets.design.SoftDreadPalette
import com.softdread.widgets.design.pressScale
import com.softdread.widgets.design.SoftDreadShape
import com.softdread.widgets.design.SoftDreadSpacing
import com.softdread.widgets.design.SoftDreadTheme
import com.softdread.widgets.design.SoftDreadType
import java.util.Locale

/**
 * The pack's own control set.
 *
 * Material 3 supplies layout and behaviour, but its default components — stadium
 * buttons, floating-label fields, a pill-indicator navigation bar, iconography —
 * read as a Material app wearing a custom palette. The design sheet is a flat,
 * editorial system: hard rules, eyebrow labels, cream fields, circles, and
 * explicitly "no icons, no illustration, no gradients". These controls are built
 * from those primitives instead.
 */

/**
 * The standard container for a row of pills.
 *
 * Owning the spacing here rather than at each call site is deliberate: the first
 * version passed only a horizontal arrangement, so wrapped rows sat flush
 * against each other. Both axes are set once, at the sheet's 10dp tile gap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PillGroup(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SoftDreadShape.TileGap),
        verticalArrangement = Arrangement.spacedBy(SoftDreadShape.TileGap),
        content = content,
    )
}

/**
 * A selectable pill. Flat fill, no elevation, no ripple-heavy Material chrome —
 * selection is carried by the field colour, exactly as a tile carries its own.
 */
@Composable
fun ChoicePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onAccent: Color? = null,
) {
    val chrome = SoftDreadTheme.chrome
    val background by animateColorAsState(
        targetValue = if (selected) (accent ?: chrome.accent) else Color.Transparent,
        animationSpec = com.softdread.widgets.design.SoftDreadMotion.settle(),
        label = "pillBackground",
    )
    val textColour = if (selected) (onAccent ?: chrome.onAccent) else chrome.onSurface
    val interactionSource = remember { MutableInteractionSource() }
    val scale = pressScale(interactionSource)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(SoftDreadShape.ChipRadius))
            .background(background)
            .then(
                if (selected) Modifier else Modifier.border(
                    BorderStroke(1.dp, chrome.hairline),
                    RoundedCornerShape(SoftDreadShape.ChipRadius),
                ),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = SoftDreadSpacing.Large, vertical = SoftDreadSpacing.Medium),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColour,
            textAlign = TextAlign.Center,
        )
    }
}

/** The primary action: a flat clay block with cream type. No elevation. */
@Composable
fun SoftDreadButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color? = null,
    onAccent: Color? = null,
) {
    val chrome = SoftDreadTheme.chrome
    val background = if (enabled) (accent ?: chrome.accent) else chrome.hairline
    val textColour = if (enabled) (onAccent ?: chrome.onAccent) else chrome.secondaryType
    val interactionSource = remember { MutableInteractionSource() }
    val scale = pressScale(interactionSource)
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(SoftDreadShape.PillRadius))
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = SoftDreadSpacing.XLarge, vertical = SoftDreadSpacing.Medium),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.lowercase(Locale.getDefault()),
            style = MaterialTheme.typography.titleMedium,
            color = textColour,
        )
    }
}

/** The secondary action: an outlined block in the same geometry. */
@Composable
fun SoftDreadOutlinedButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val chrome = SoftDreadTheme.chrome
    val interactionSource = remember { MutableInteractionSource() }
    val scale = pressScale(interactionSource)
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(SoftDreadShape.PillRadius))
            .border(BorderStroke(1.dp, chrome.onSurface), RoundedCornerShape(SoftDreadShape.PillRadius))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = SoftDreadSpacing.XLarge, vertical = SoftDreadSpacing.Medium),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.lowercase(Locale.getDefault()),
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) chrome.onSurface else chrome.secondaryType,
        )
    }
}

/** A quieter inline action, set as a micro-label rather than as a button. */
@Composable
fun SoftDreadTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colour: Color? = null,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(SoftDreadShape.ChipRadius))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = SoftDreadSpacing.Medium, vertical = SoftDreadSpacing.Small),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label.uppercase(Locale.getDefault()),
            style = SoftDreadType.SectionLabel,
            color = colour ?: SoftDreadTheme.chrome.accentText,
        )
    }
}

/**
 * A flat text field: an eyebrow label above, a cream well below. No floating
 * label, no Material underline animation.
 */
@Composable
fun SoftDreadField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
) {
    val chrome = SoftDreadTheme.chrome
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val border by animateColorAsState(
        targetValue = if (focused) chrome.accentText else chrome.hairline,
        label = "fieldBorder",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(label)
        Spacer(Modifier.height(SoftDreadSpacing.Medium))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clip(RoundedCornerShape(SoftDreadShape.PillRadius))
                .background(chrome.surface)
                .border(BorderStroke(if (focused) 2.dp else 1.dp, border), RoundedCornerShape(SoftDreadShape.PillRadius))
                .padding(horizontal = SoftDreadSpacing.Large, vertical = SoftDreadSpacing.Medium),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = chrome.secondaryType,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                interactionSource = interactionSource,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyLarge.copy(color = chrome.onSurface),
                ),
                cursorBrush = SolidColor(chrome.accentText),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * A flat two-state toggle built from the pack's own shapes — a track and a
 * circle — rather than a Material switch.
 */
@Composable
fun SoftDreadToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = SoftDreadTheme.chrome
    val trackColour by animateColorAsState(
        targetValue = if (checked) chrome.accent else chrome.hairline,
        label = "toggleTrack",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 3.dp,
        label = "toggleKnob",
    )
    Box(
        modifier = modifier
            .size(width = 51.dp, height = 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(trackColour)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) },
    ) {
        Box(
            modifier = Modifier
                .padding(start = knobOffset)
                .align(Alignment.CenterStart)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) chrome.onAccent else chrome.surface),
        )
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
            .heightIn(min = 56.dp)
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
        Spacer(Modifier.width(SoftDreadSpacing.Large))
        SoftDreadToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** The sheet's structural device: a hard rule, 2px under a masthead, 1px elsewhere. */
@Composable
fun Rule(modifier: Modifier = Modifier, thickness: Dp = 1.dp, colour: Color? = null) {
    val chrome = SoftDreadTheme.chrome
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .background(colour ?: chrome.hairline),
    )
}

/** Mono annotation, the sheet's voice for specs and metadata. */
@Composable
fun SpecLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = SoftDreadType.Mono,
        color = SoftDreadTheme.chrome.secondaryType,
        modifier = modifier,
    )
}

/** A small filled circle — the pack's recurring mark, used as a status dot. */
@Composable
fun Dot(colour: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(modifier = modifier.size(size).clip(CircleShape).background(colour))
}

/**
 * Centres content and caps its line length on wide windows. A settings column
 * stretched across a 12" tablet is not "responsive", it is just wide; the cap
 * keeps measure comfortable and the wallpaper (and its drifting circles)
 * visible at the edges.
 */
@Composable
fun ContentFrame(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 720.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxWidth()) { content() }
    }
}
