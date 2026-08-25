package com.softdread.widgets.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.softdread.widgets.design.CircleField
import com.softdread.widgets.design.SoftDreadMotion
import com.softdread.widgets.design.SoftDreadSpacing
import com.softdread.widgets.design.SoftDreadTheme
import com.softdread.widgets.design.breathing
import com.softdread.widgets.design.entrance
import com.softdread.widgets.domain.model.Personality
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.ui.blurbRes
import com.softdread.widgets.ui.components.WitgetMark
import com.softdread.widgets.ui.components.ChoicePill
import com.softdread.widgets.ui.components.PillGroup
import com.softdread.widgets.ui.components.PreviewTile
import com.softdread.widgets.ui.components.Rule
import com.softdread.widgets.ui.components.SoftDreadButton
import com.softdread.widgets.ui.components.SoftDreadTextAction
import com.softdread.widgets.ui.components.SpecLine
import com.softdread.widgets.ui.labelRes
import com.softdread.widgets.ui.preview.SampleData
import com.softdread.widgets.widgets.common.LeadingVisual
import com.softdread.widgets.widgets.common.TileContent

/**
 * Three-screen onboarding.
 *
 * It asks for nothing: the only interactive step is choosing a personality,
 * which is a preference rather than a permission, and Skip is always present.
 * The pack's circles drift behind the whole flow, pages slide with a spring,
 * and every element enters on the shared stagger — the motion is the first
 * thing the user sees, so it carries the product's whole first impression.
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

    Box(modifier = modifier.fillMaxSize().background(chrome.wallpaper)) {
        CircleField(isDark = chrome.isDark)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(SoftDreadSpacing.XLarge),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().entrance(0),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.breathing()) { WitgetMark(size = 48) }
                Spacer(Modifier.width(SoftDreadSpacing.Medium))
                SpecLine("WITGET · ${page + 1}/$pages")
                Spacer(Modifier.weight(1f))
                SoftDreadTextAction(
                    label = stringResource(R.string.onboarding_skip),
                    onClick = onFinish,
                    colour = chrome.secondaryType,
                )
            }

            Spacer(Modifier.height(SoftDreadSpacing.Large))
            Box(Modifier.entrance(1)) {
                Rule(thickness = 2.dp, colour = chrome.onSurface)
            }
            Spacer(Modifier.height(SoftDreadSpacing.XXLarge))

            // Pages slide as a block: forward = new page in from the right.
            AnimatedContent(
                targetState = page,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val forward = targetState > initialState
                    val enter = slideInHorizontally(SoftDreadMotion.settle()) {
                        if (forward) it / 3 else -it / 3
                    } + fadeIn(SoftDreadMotion.settle())
                    val exit = slideOutHorizontally(SoftDreadMotion.settle()) {
                        if (forward) -it / 3 else it / 3
                    } + fadeOut(SoftDreadMotion.settle())
                    enter togetherWith exit
                },
                label = "onboardingPage",
            ) { current ->
                when (current) {
                    0 -> OnboardingPage(
                        titleRes = R.string.onboarding_1_title,
                        bodyRes = R.string.onboarding_1_body,
                    ) {
                        PreviewTile(
                            role = WidgetType.BATTERY.colourRole,
                            breakpoint = WidgetBreakpoint.COMPACT,
                            content = batterySample(),
                            isDark = chrome.isDark,
                            modifier = Modifier.width(190.dp),
                        )
                    }

                    1 -> OnboardingPage(
                        titleRes = R.string.onboarding_2_title,
                        bodyRes = R.string.onboarding_2_body,
                    ) {
                        Column {
                            PillGroup {
                                Personality.entries.forEach { personality ->
                                    ChoicePill(
                                        label = stringResource(personality.labelRes),
                                        selected = personality == selectedPersonality,
                                        onClick = { onSelectPersonality(personality) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(SoftDreadSpacing.Large))
                            AnimatedContent(
                                targetState = selectedPersonality,
                                transitionSpec = {
                                    fadeIn(SoftDreadMotion.settle()) togetherWith
                                        fadeOut(SoftDreadMotion.settle())
                                },
                                label = "personalityBlurb",
                            ) { personality ->
                                Text(
                                    text = stringResource(personality.blurbRes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = chrome.secondaryType,
                                )
                            }
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
                            modifier = Modifier.width(190.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().entrance(4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SoftDreadSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(pages) { index ->
                        // The active dot stretches into a lozenge; the spring
                        // gives the handoff a physical snap.
                        val dotWidth by animateDpAsState(
                            targetValue = if (index == page) 26.dp else 8.dp,
                            animationSpec = SoftDreadMotion.pop(),
                            label = "dotWidth",
                        )
                        Box(
                            modifier = Modifier
                                .width(dotWidth)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == page) chrome.brandDot
                                    else chrome.onSurface.copy(alpha = 0.2f),
                                ),
                        )
                    }
                }
                SoftDreadButton(
                    label = stringResource(
                        if (page < pages - 1) R.string.onboarding_next else R.string.onboarding_done,
                    ),
                    onClick = { if (page < pages - 1) page++ else onFinish() },
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
        Box(Modifier.entrance(0)) { illustration() }
        Spacer(Modifier.height(SoftDreadSpacing.XXLarge))
        Box(Modifier.entrance(2)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineLarge,
                color = chrome.onSurface,
            )
        }
        Spacer(Modifier.height(SoftDreadSpacing.Medium))
        Box(Modifier.entrance(3)) {
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyLarge,
                color = chrome.secondaryType,
            )
        }
    }
}

/** The design sheet's own reference values, identical on every device. */
private fun batterySample() = TileContent(
    label = "battery",
    heroValue = "23%",
    voice = "optimism is no longer appropriate",
    leading = LeadingVisual.Ring(0.23f),
    contentDescription = "Example: battery 23 percent. Optimism is no longer appropriate.",
)
