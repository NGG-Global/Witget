package com.softdread.widgets.data.prefs

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.softdread.widgets.domain.model.Personality
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.domain.selection.HistoryEntry
import com.softdread.widgets.domain.selection.PoolHistory
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Per-instance configuration and history.
 *
 * The product requirement this covers is that two widgets of the same type never
 * share state: two countdowns hold different targets, two 8 balls hold different
 * personalities and different anti-repeat histories, and deleting one leaves the
 * other untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetInstanceConfigTest {

    private val store = SoftDreadStore(ApplicationProvider.getApplicationContext())

    @Test
    fun `personality falls back to the global default until overridden`() {
        val config = WidgetInstanceConfig.default(1, WidgetType.BATTERY)
        assertThat(config.personality(Personality.SARCASTIC)).isEqualTo(Personality.SARCASTIC)
        assertThat(config.copy(personalityKey = "DRY").personality(Personality.SARCASTIC))
            .isEqualTo(Personality.DRY)
    }

    @Test
    fun `an unknown personality key degrades to the default rather than crashing`() {
        val config = WidgetInstanceConfig.default(1, WidgetType.BATTERY).copy(personalityKey = "BAROQUE")
        assertThat(config.personality(Personality.DRY)).isEqualTo(Personality.NEUTRAL)
    }

    @Test
    fun `instance keys are unique per placed widget`() {
        val a = WidgetInstanceConfig.default(11, WidgetType.COUNTDOWN)
        val b = WidgetInstanceConfig.default(12, WidgetType.COUNTDOWN)
        assertThat(a.instanceKey).isNotEqualTo(b.instanceKey)
    }

    @Test
    fun `a countdown is unconfigured until it has both a title and a target`() {
        val blank = WidgetInstanceConfig.default(1, WidgetType.COUNTDOWN)
        assertThat(blank.isConfigured).isFalse()
        assertThat(blank.copy(countdownTitle = "Japan").isConfigured).isFalse()
        assertThat(
            blank.copy(countdownTitle = "Japan", countdownTargetEpochMillis = 1L).isConfigured,
        ).isTrue()
        // Everything else is useful the moment it is placed.
        assertThat(WidgetInstanceConfig.default(1, WidgetType.BATTERY).isConfigured).isTrue()
    }

    @Test
    fun `two instances of one type hold independent configuration`() = runBlocking<Unit> {
        store.saveConfig(
            WidgetInstanceConfig.default(101, WidgetType.COUNTDOWN)
                .copy(countdownTitle = "Japan", countdownTargetEpochMillis = 1_000L, personalityKey = "DRY"),
        )
        store.saveConfig(
            WidgetInstanceConfig.default(102, WidgetType.COUNTDOWN)
                .copy(countdownTitle = "Birthday", countdownTargetEpochMillis = 2_000L, personalityKey = "CHAOTIC"),
        )

        val japan = store.currentConfig(101)!!
        val birthday = store.currentConfig(102)!!
        assertThat(japan.countdownTitle).isEqualTo("Japan")
        assertThat(birthday.countdownTitle).isEqualTo("Birthday")
        assertThat(japan.personality(Personality.NEUTRAL)).isEqualTo(Personality.DRY)
        assertThat(birthday.personality(Personality.NEUTRAL)).isEqualTo(Personality.CHAOTIC)
    }

    @Test
    fun `history is scoped to the instance and to the pool`() = runBlocking<Unit> {
        val first = WidgetInstanceConfig.default(201, WidgetType.MAGIC_8_BALL)
        val second = WidgetInstanceConfig.default(202, WidgetType.MAGIC_8_BALL)
        store.saveConfig(first)
        store.saveConfig(second)

        store.recordHistory(first.instanceKey, "answers", PoolHistory(listOf(HistoryEntry("A", 1L))))
        store.recordHistory(second.instanceKey, "answers", PoolHistory(listOf(HistoryEntry("B", 1L))))

        assertThat(store.poolHistory(first.instanceKey, "answers").recordedIds()).containsExactly("A")
        assertThat(store.poolHistory(second.instanceKey, "answers").recordedIds()).containsExactly("B")
        assertThat(store.poolHistory(first.instanceKey, "other").entries).isEmpty()
    }

    @Test
    fun `deleting one instance removes its config and history but not its neighbour's`() = runBlocking<Unit> {
        val keep = WidgetInstanceConfig.default(301, WidgetType.BATTERY)
        val remove = WidgetInstanceConfig.default(302, WidgetType.BATTERY)
        store.saveConfig(keep)
        store.saveConfig(remove)
        store.recordHistory(keep.instanceKey, "p", PoolHistory(listOf(HistoryEntry("K", 1L))))
        store.recordHistory(remove.instanceKey, "p", PoolHistory(listOf(HistoryEntry("R", 1L))))

        store.deleteInstance(302)

        assertThat(store.currentConfig(302)).isNull()
        assertThat(store.poolHistory(remove.instanceKey, "p").entries).isEmpty()
        assertThat(store.currentConfig(301)).isNotNull()
        assertThat(store.poolHistory(keep.instanceKey, "p").recordedIds()).containsExactly("K")
    }

    @Test
    fun `orphaned configuration is pruned when the host forgets a widget`() = runBlocking<Unit> {
        store.saveConfig(WidgetInstanceConfig.default(401, WidgetType.WEATHER))
        store.saveConfig(WidgetInstanceConfig.default(402, WidgetType.WEATHER))

        store.pruneOrphans(liveAppWidgetIds = setOf(401))

        assertThat(store.currentConfig(401)).isNotNull()
        assertThat(store.currentConfig(402)).isNull()
    }

    @Test
    fun `configOrCreate is idempotent and survives a type change`() = runBlocking<Unit> {
        val created = store.configOrCreate(501, WidgetType.TIME_PROGRESS)
        val again = store.configOrCreate(501, WidgetType.TIME_PROGRESS)
        assertThat(again).isEqualTo(created)

        // A recycled appWidgetId bound to a different provider gets a fresh record.
        val replaced = store.configOrCreate(501, WidgetType.BATTERY)
        assertThat(replaced.widgetTypeId).isEqualTo(WidgetType.BATTERY.id)
    }

    @Test
    fun `global preferences round-trip and default sensibly`() = runBlocking<Unit> {
        assertThat(store.currentPreferences().defaultPersonality).isEqualTo(Personality.NEUTRAL)
        store.updatePreferences { it.copy(defaultPersonalityKey = "CHAOTIC", useCelsius = false) }
        val updated = store.currentPreferences()
        assertThat(updated.defaultPersonality).isEqualTo(Personality.CHAOTIC)
        assertThat(updated.useCelsius).isFalse()
    }

    @Test
    fun `a new instance inherits the type's template`() = runBlocking<Unit> {
        store.saveConfig(
            WidgetInstanceConfig.template(WidgetType.TIME_PROGRESS)
                .copy(progressScopeKey = "week", personalityKey = "DRY"),
        )

        val placed = store.configOrCreate(701, WidgetType.TIME_PROGRESS)

        assertThat(placed.appWidgetId).isEqualTo(701)
        assertThat(placed.isTemplate).isFalse()
        assertThat(placed.progressScope).isEqualTo(ProgressScope.WEEK)
        assertThat(placed.personality(Personality.NEUTRAL)).isEqualTo(Personality.DRY)
    }

    @Test
    fun `editing a placed instance does not disturb the template or its siblings`() = runBlocking<Unit> {
        store.saveConfig(
            WidgetInstanceConfig.template(WidgetType.MAGIC_8_BALL).copy(personalityKey = "FRIENDLY"),
        )
        val first = store.configOrCreate(801, WidgetType.MAGIC_8_BALL)
        val second = store.configOrCreate(802, WidgetType.MAGIC_8_BALL)
        assertThat(first.personality(Personality.NEUTRAL)).isEqualTo(Personality.FRIENDLY)

        store.saveConfig(second.copy(personalityKey = "CHAOTIC"))

        assertThat(store.currentConfig(801)!!.personalityKey).isEqualTo("FRIENDLY")
        assertThat(store.template(WidgetType.MAGIC_8_BALL).personalityKey).isEqualTo("FRIENDLY")
    }

    @Test
    fun `templates are never counted as placed widgets and survive pruning`() = runBlocking<Unit> {
        store.saveConfig(WidgetInstanceConfig.template(WidgetType.WEATHER))
        store.saveConfig(WidgetInstanceConfig.default(901, WidgetType.WEATHER))

        assertThat(store.placedConfigs().map { it.appWidgetId }).contains(901)
        assertThat(store.placedConfigs().none { it.isTemplate }).isTrue()

        store.pruneOrphans(liveAppWidgetIds = emptySet())

        assertThat(store.currentConfig(901)).isNull()
        assertThat(store.currentConfig(WidgetInstanceConfig.templateId(WidgetType.WEATHER))).isNotNull()
    }

    @Test
    fun `each widget type gets a distinct template id`() {
        val ids = WidgetType.entries.map { WidgetInstanceConfig.templateId(it) }
        assertThat(ids).containsNoDuplicates()
        // Every template id must sit below any id the platform can allocate.
        ids.forEach { assertThat(it).isAtMost(WidgetInstanceConfig.TEMPLATE_ID_BASE) }
    }

    @Test
    fun `history is bounded when recorded through the store`() = runBlocking<Unit> {
        val config = WidgetInstanceConfig.default(601, WidgetType.DAILY_JOKE)
        store.saveConfig(config)
        var history = PoolHistory()
        repeat(200) { index ->
            history = history.plus(HistoryEntry("J$index", index.toLong()), maxEntries = 130)
        }
        store.recordHistory(config.instanceKey, "jokes", history)
        assertThat(store.poolHistory(config.instanceKey, "jokes").entries).hasSize(130)
    }
}
