package com.softdread.widgets.data.content

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.softdread.widgets.data.prefs.EquivalencyCategory
import com.softdread.widgets.data.prefs.JokeCategory
import com.softdread.widgets.domain.model.ColourRole
import com.softdread.widgets.domain.model.Personality
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.domain.selection.Interpolation
import com.softdread.widgets.widgets.common.MotifGlyph
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Validates the entire generated content pack.
 *
 * This is the safety net for `tools/content/extract_content.py`: if the Content
 * Bible is edited and the extractor is re-run, a malformed row, a missing
 * personality, a duplicate ID or a typo'd variable fails here rather than
 * surfacing as a broken tile on someone's home screen.
 *
 * The expected counts are the Bible's own section 11 inventory.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentPackTest {

    private val repository = ContentRepository(ApplicationProvider.getApplicationContext())

    /** Every variable the Content Bible defines, across all widgets. */
    private val knownVariables = setOf(
        "percent", "remaining_percent", "minutes", "hours", "hours_short",
        "ratio", "equivalent", "temp", "feels", "uv", "wind", "rain_chance",
        "event", "event_upper", "days", "sleeps", "weeks", "weekends", "months", "years",
        "period", "period_upper", "first_time", "late_time", "meeting_count",
        "booked_hours", "free_hours", "score", "day_of_year", "category",
    )

    /** Response counts per widget, from the Bible's implementation checklist. */
    private val expectedResponseCounts = mapOf(
        WidgetType.SCREEN_TIME to 140, // 120 band responses + 20 equivalency templates
        WidgetType.DAILY_JOKE to 40,
        WidgetType.BATTERY to 240,
        WidgetType.DAY_VIBE to 200,
        WidgetType.WEATHER to 220,
        WidgetType.COUNTDOWN to 160,
        WidgetType.TIME_PROGRESS to 180,
        WidgetType.MAGIC_8_BALL to 150,
    )

    /** The states each widget's logic can ask for; all must exist in the pack. */
    private val requiredStates = mapOf(
        WidgetType.SCREEN_TIME to listOf("ST_B1", "ST_B2", "ST_B3", "ST_B4", "ST_B5", "ST_B6", "ST_EQ"),
        WidgetType.DAILY_JOKE to listOf("J_META"),
        WidgetType.BATTERY to listOf(
            "BAT_B1", "BAT_B2", "BAT_B3", "BAT_B4", "BAT_B5", "BAT_B6", "BAT_B7", "BAT_B8",
            "BAT_CH1", "BAT_CH2", "BAT_CH3", "BAT_CH4",
        ),
        WidgetType.DAY_VIBE to listOf(
            "DAY_B1", "DAY_B2", "DAY_B3", "DAY_B4", "DAY_B5",
            "DAY_S1", "DAY_S2", "DAY_S3", "DAY_S4", "DAY_S5",
        ),
        WidgetType.WEATHER to (1..11).map { "WX$it" },
        WidgetType.COUNTDOWN to (1..8).map { "CD$it" },
        WidgetType.TIME_PROGRESS to listOf("TP1", "TP2", "TP3", "TP4", "TP5", "TP_S1", "TP_S2", "TP_S3", "TP_S4"),
        WidgetType.MAGIC_8_BALL to listOf("M8_POSITIVE", "M8_NEUTRAL", "M8_NEGATIVE"),
    )

    @Test
    fun `every widget's content asset parses`() = runBlocking<Unit> {
        WidgetType.entries.forEach { type ->
            val document = repository.document(type)
            assertThat(document.schemaVersion).isEqualTo(1)
            assertThat(document.locale).isEqualTo("en")
            assertThat(document.states).isNotEmpty()
        }
    }

    @Test
    fun `response counts match the Bible inventory`() = runBlocking<Unit> {
        expectedResponseCounts.forEach { (type, expected) ->
            val count = repository.document(type).states.sumOf { state ->
                state.pools.sumOf { it.responses.size }
            }
            assertThat(count).isEqualTo(expected)
        }
    }

    @Test
    fun `every widget carries every state its logic can request`() = runBlocking<Unit> {
        requiredStates.forEach { (type, states) ->
            val present = repository.document(type).states.map { it.key }
            assertThat(present).containsAtLeastElementsIn(states)
        }
    }

    @Test
    fun `every state has a pool for every personality`() = runBlocking<Unit> {
        WidgetType.entries.forEach { type ->
            val document = repository.document(type)
            document.states.forEach { state ->
                val modes = state.pools.map { it.mode }
                assertThat(modes).containsExactlyElementsIn(Personality.entries.map { it.key })
                state.pools.forEach { pool ->
                    assertThat(pool.responses).isNotEmpty()
                }
            }
        }
    }

    @Test
    fun `response ids are globally unique and follow the Bible's naming pattern`() = runBlocking<Unit> {
        val pattern = Regex("^[A-Z0-9_]+_(NEUTRAL|FRIENDLY|DRY|SARCASTIC|CHAOTIC)_\\d{2}$")
        val seen = mutableSetOf<String>()
        WidgetType.entries.forEach { type ->
            repository.document(type).states.forEach { state ->
                state.pools.forEach { pool ->
                    pool.responses.forEach { response ->
                        assertThat(response.id).matches(pattern.pattern)
                        assertThat(response.id).startsWith("${state.key}_${pool.mode}_")
                        assertThat(seen.add(response.id)).isTrue()
                    }
                }
            }
        }
        assertThat(seen).hasSize(expectedResponseCounts.values.sum())
    }

    @Test
    fun `no response is blank, malformed or carries an unknown variable`() = runBlocking<Unit> {
        WidgetType.entries.forEach { type ->
            repository.document(type).states.forEach { state ->
                state.pools.forEach { pool ->
                    pool.responses.forEach { response ->
                        assertThat(response.text.trim()).isNotEmpty()
                        // Braces must be balanced, or a placeholder would leak.
                        assertThat(response.text.count { it == '{' })
                            .isEqualTo(response.text.count { it == '}' })
                        val used = Interpolation.variablesIn(response.text)
                        assertThat(knownVariables).containsAtLeastElementsIn(used)
                        // The declared variable list must match the text exactly,
                        // because selection filters on it.
                        assertThat(response.requiredVariables.toSet()).isEqualTo(used)
                    }
                }
            }
        }
    }

    @Test
    fun `no response is unusable at the widest tile`() = runBlocking<Unit> {
        // The design sheet's largest copy budget is 96 characters at 4x4; a
        // response far beyond that could never be shown at any size.
        WidgetType.entries.forEach { type ->
            repository.document(type).states.forEach { state ->
                state.pools.forEach { pool ->
                    pool.responses.forEach { response ->
                        assertThat(response.text.length).isLessThan(220)
                    }
                }
            }
        }
    }

    @Test
    fun `the equivalency library is complete and usable`() = runBlocking<Unit> {
        val units = repository.document(WidgetType.SCREEN_TIME).equivalencyUnits
        assertThat(units).hasSize(50)
        assertThat(units.map { it.id }).containsNoDuplicates()
        val categories = EquivalencyCategory.entries.map { it.key }.toSet()
        units.forEach { unit ->
            assertThat(unit.minutes).isGreaterThan(0.0)
            assertThat(unit.label.trim()).isNotEmpty()
            assertThat(categories).contains(unit.category)
        }
    }

    @Test
    fun `the joke library is complete and correctly categorised`() = runBlocking<Unit> {
        val document = repository.document(WidgetType.DAILY_JOKE)
        assertThat(document.jokes).hasSize(120)
        assertThat(document.jokes.map { it.id }).containsNoDuplicates()
        assertThat(document.jokes.map { it.text }).containsNoDuplicates()

        val categories = JokeCategory.entries.map { it.key }.toSet()
        document.jokes.forEach { joke ->
            assertThat(joke.text.trim()).isNotEmpty()
            assertThat(categories).contains(joke.category)
        }
        // The Bible specifies 24 jokes in each of the five categories.
        document.jokes.groupBy { it.category }.forEach { (_, jokes) ->
            assertThat(jokes).hasSize(24)
        }
    }

    @Test
    fun `every joke and equivalency unit carries a valid curated motif`() = runBlocking<Unit> {
        // The extractor's curated names and the MotifGlyph/ColourRole enums are
        // two spellings of one set; this is the test that keeps them from
        // drifting. A joke or unit without a motif would silently render a
        // plain tile — the whole point of the feature is that none do.
        val glyphNames = MotifGlyph.entries.map { it.name }.toSet()
        val colourNames = ColourRole.entries.map { it.name }.toSet()

        repository.document(WidgetType.DAILY_JOKE).jokes.forEach { joke ->
            assertWithMessage("joke ${joke.id} glyph").that(joke.glyph).isIn(glyphNames)
            assertWithMessage("joke ${joke.id} colour").that(joke.colour).isIn(colourNames)
        }
        repository.document(WidgetType.SCREEN_TIME).equivalencyUnits.forEach { unit ->
            assertWithMessage("unit ${unit.id} glyph").that(unit.glyph).isIn(glyphNames)
        }
    }

    @Test
    fun `joke category weights are defined for every personality and sum to one hundred`() = runBlocking<Unit> {
        val weights = repository.document(WidgetType.DAILY_JOKE).categoryWeights
        assertThat(weights.map { it.mode }).containsExactlyElementsIn(Personality.entries.map { it.key })
        weights.forEach { weighting ->
            assertThat(weighting.weights.sumOf { it.weight }).isEqualTo(100)
            assertThat(weighting.weights.map { it.category })
                .containsExactlyElementsIn(JokeCategory.entries.map { it.key })
        }
    }

    @Test
    fun `the manifest agrees with the files it describes`() = runBlocking<Unit> {
        val manifest = repository.manifest()
        assertThat(manifest.schemaVersion).isEqualTo(1)
        assertThat(manifest.source.document).isEqualTo("widget_pack_content_bible_v1.docx")
        assertThat(manifest.source.sha256).hasLength(64)
        assertThat(manifest.totalResponseCount).isEqualTo(expectedResponseCounts.values.sum())
        assertThat(manifest.jokeCount).isEqualTo(120)
        assertThat(manifest.equivalencyUnitCount).isEqualTo(50)
        assertThat(manifest.files).hasSize(8)
    }

    @Test
    fun `pool lookup falls back to Neutral rather than returning nothing`() = runBlocking<Unit> {
        val document = repository.document(WidgetType.BATTERY)
        Personality.entries.forEach { personality ->
            assertThat(document.pool("BAT_B1", personality)).isNotEmpty()
        }
        // An unknown state yields an empty pool, which callers treat as "no copy".
        assertThat(document.pool("NOT_A_STATE", Personality.NEUTRAL)).isEmpty()
    }
}
