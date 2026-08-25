package com.softdread.widgets.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.softdread.widgets.R
import com.softdread.widgets.design.SoftDreadTheme
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.ui.components.PreviewTile
import com.softdread.widgets.ui.preview.SampleData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for the app's core surfaces.
 *
 * These require a connected device or emulator:
 *
 *     ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class GalleryFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun previewTileRendersItsContentAndStaysAccessible() {
        val content = SampleData.content(WidgetType.SCREEN_TIME, WidgetBreakpoint.COMPACT)
        composeRule.setContent {
            SoftDreadTheme { PreviewTile(WidgetType.SCREEN_TIME.colourRole, WidgetBreakpoint.COMPACT, content) }
        }
        // The literal metric must remain readable alongside the interpretation.
        composeRule.onNodeWithText("6H 30M").assertIsDisplayed()
        composeRule.onNodeWithText("0.7").assertIsDisplayed()
    }

    @Test
    fun everyWidgetTypeRendersAtEveryBreakpoint() {
        // A smoke test over the whole matrix: no layout may throw, and the
        // micro-label is always present so the tile is identifiable.
        WidgetType.entries.forEach { type ->
            listOf(
                WidgetBreakpoint.COMPACT,
                WidgetBreakpoint.STANDARD,
                WidgetBreakpoint.EXPANDED,
            ).forEach { breakpoint ->
                val content = SampleData.content(type, breakpoint)
                composeRule.setContent {
                    SoftDreadTheme { PreviewTile(type.colourRole, breakpoint, content) }
                }
                composeRule.onNodeWithText(content.label.uppercase()).assertIsDisplayed()
            }
        }
    }

    @Test
    fun onboardingLetsTheUserReachTheGalleryWithoutGrantingAnything() {
        var finished = false
        composeRule.setContent {
            SoftDreadTheme {
                com.softdread.widgets.ui.onboarding.OnboardingScreen(
                    selectedPersonality = com.softdread.widgets.domain.model.Personality.NEUTRAL,
                    onSelectPersonality = {},
                    onFinish = { finished = true },
                )
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.onboarding_skip)).performClick()
        assert(finished) { "Skip should reach the gallery without any permission prompt" }
    }

    @Test
    fun personalitySelectionIsReportedBack() {
        var selected: com.softdread.widgets.domain.model.Personality? = null
        composeRule.setContent {
            SoftDreadTheme {
                com.softdread.widgets.ui.onboarding.OnboardingScreen(
                    selectedPersonality = com.softdread.widgets.domain.model.Personality.NEUTRAL,
                    onSelectPersonality = { selected = it },
                    onFinish = {},
                )
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.onboarding_next)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.personality_sarcastic))
            .performScrollTo()
            .performClick()
        assert(selected == com.softdread.widgets.domain.model.Personality.SARCASTIC)
    }
}
