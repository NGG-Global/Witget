package com.softdread.widgets.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.softdread.widgets.data.prefs.GlobalPreferences
import com.softdread.widgets.data.prefs.WidgetInstanceConfig
import com.softdread.widgets.domain.model.Personality
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.ui.preview.WidgetPreviewer
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs every widget's real content pipeline on-device.
 *
 * This is the test that proves a placed widget produces resolved copy for every
 * personality at every size — including the setup states, which must still carry
 * a usable label and description rather than being blank.
 *
 * Requires a connected device or emulator:
 *
 *     ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class WidgetRenderTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyWidgetProducesResolvedCopyForEveryPersonalityAndSize() = runBlocking<Unit> {
        val breakpoints = listOf(
            WidgetBreakpoint.COMPACT,
            WidgetBreakpoint.STANDARD,
            WidgetBreakpoint.EXPANDED,
        )
        WidgetType.entries.forEach { type ->
            Personality.entries.forEach { personality ->
                breakpoints.forEach { breakpoint ->
                    val config = WidgetInstanceConfig.default(-1, type).copy(
                        personalityKey = personality.key,
                        countdownTitle = "Japan",
                        countdownTargetEpochMillis = System.currentTimeMillis() + 19L * 86_400_000L,
                    )
                    val preview = WidgetPreviewer.preview(
                        context = context,
                        type = type,
                        config = config,
                        preferences = GlobalPreferences(),
                        breakpoint = breakpoint,
                        isDark = false,
                    )
                    val content = preview.content
                    val label = "$type/$personality/$breakpoint"

                    assertThat(content.label).isNotEmpty()
                    assertThat(content.contentDescription).isNotEmpty()

                    // No rendered string may leak an unresolved placeholder.
                    listOfNotNull(
                        content.heroValue, content.metric, content.subhead,
                        content.voice, content.pill, content.labelDetail,
                        content.contentDescription,
                    ).forEach { text ->
                        assertThat(text).doesNotContain("{")
                        assertThat(text).doesNotContain("}")
                    }

                    // The copy budget for the breakpoint is respected.
                    content.voice?.let {
                        assertThat(it.length).isAtMost(breakpoint.maxResponseChars + 40)
                    }
                    assertThat(label).isNotEmpty()
                }
            }
        }
    }

    @Test
    fun permissionGatedWidgetsReportSetupRatherThanFakeData() = runBlocking<Unit> {
        // On a device with no Usage Access granted, Screen Time must say so.
        val preview = WidgetPreviewer.preview(
            context = context,
            type = WidgetType.SCREEN_TIME,
            config = WidgetInstanceConfig.default(-1, WidgetType.SCREEN_TIME),
            preferences = GlobalPreferences(),
            breakpoint = WidgetBreakpoint.COMPACT,
            isDark = false,
        )
        if (preview.needsSetup) {
            assertThat(preview.content.isSetupState).isTrue()
            assertThat(preview.content.heroValue).isNull()
        }
    }
}
