package com.softdread.widgets.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.softdread.widgets.R
import com.softdread.widgets.design.SoftDreadPalette
import com.softdread.widgets.design.SoftDreadSpacing
import com.softdread.widgets.design.SoftDreadTheme
import com.softdread.widgets.domain.model.Personality
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.ui.blurbRes
import com.softdread.widgets.ui.components.AppMark
import com.softdread.widgets.ui.components.ChoicePill
import com.softdread.widgets.ui.components.PreviewTile
import com.softdread.widgets.ui.labelRes
import com.softdread.widgets.ui.preview.SampleData
import com.softdread.widgets.widgets.common.TileContent

/**
 * Three-screen onboarding.
 *
 * It asks for nothing. The only interactive step is choosing a default
 * personality, which is a preference rather than a permission; the user reaches
 * the gallery and can browse every widget before granting anything.
 */
@Composable
fun OnboardingScreen(
    selectedPersonality: Personality,
    onSelectPersonality: (Personality) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = SoftDreadTheme.chrome
    var page by remember { mutableIntStateOf(0) }
    val pages = 3

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(SoftDreadSpacing.XLarge),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppMark(size = 48)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onFinish) { Text(stringResource(R.string.onboarding_skip)) }
        }

        Spacer(Modifier.height(SoftDreadSpacing.XXLarge))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (page) {
                0 -> OnboardingPage(
                    titleRes = R.string.onboarding_1_title,
                    bodyRes = R.string.onboarding_1_body,
                ) {
                    PreviewTile(
                        role = WidgetType.BATTERY.colourRole,
                        breakpoint = WidgetBreakpoint.COMPACT,
                        content = batterySample(),
                        isDark = chrome.isDark,
                        modifier = Modifier.width(180.dp),
                    )
                }

                1 -> OnboardingPage(
                    titleRes = R.string.onboarding_2_title,
                    bodyRes = R.string.onboarding_2_body,
                ) {
                    Column {
                        Personality.entries.forEach { personality ->
                            ChoicePill(
                                label = stringResource(personality.labelRes),
                                selected = personality == selectedPersonality,
                                onClick = { onSelectPersonality(personality) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(SoftDreadSpacing.Small))
                        }
                        Spacer(Modifier.height(SoftDreadSpacing.Small))
                        Text(
                            text = stringResource(selectedPersonality.blurbRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = chrome.secondaryType,
                        )
                    }
                }

                else -> OnboardingPage(
                    titleRes = R.string.onboarding_3_title,
                    bodyRes = R.string.onboarding_3_body,
                ) {
                    PreviewTile(
                        role = WidgetType.SCREEN_TIME.colourRole,
                        breakpoint = WidgetBreakpoint.COMPACT,
                        content = SampleData.content(WidgetType.SCREEN_TIME, WidgetBreakpoint.COMPACT),
                        isDark = chrome.isDark,
                        modifier = Modifier.width(180.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(SoftDreadSpacing.Small)) {
                repeat(pages) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == page) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == page) SoftDreadPalette.Clay else chrome.onSurface.copy(alpha = 0.2f),
                            ),
                    )
                }
            }
            Button(
                onClick = { if (page < pages - 1) page++ else onFinish() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SoftDreadPalette.Clay,
                    contentColor = SoftDreadPalette.TypeOnColour,
                ),
            ) {
                Text(
                    stringResource(
                        if (page < pages - 1) R.string.onboarding_next else R.string.onboarding_done,
                    ),
                )
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    titleRes: Int,
    bodyRes: Int,
    illustration: @Composable () -> Unit,
) {
    val chrome = SoftDreadTheme.chrome
    Column(modifier = Modifier.fillMaxSize()) {
        illustration()
        Spacer(Modifier.height(SoftDreadSpacing.XXLarge))
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineLarge,
            color = chrome.onSurface,
        )
        Spacer(Modifier.height(SoftDreadSpacing.Medium))
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = chrome.secondaryType,
        )
    }
}

/**
 * The onboarding tile shows the design sheet's own reference values rather than
 * reading the real battery, so the illustration is identical on every device.
 */
private fun batterySample() = TileContent(
    label = "battery",
    heroValue = "23%",
    voice = "optimism is no longer appropriate",
    leading = com.softdread.widgets.widgets.common.LeadingVisual.Ring(0.23f),
    contentDescription = "Example: battery 23 percent. Optimism is no longer appropriate.",
)
