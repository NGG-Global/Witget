package com.softdread.widgets.domain.logic

import com.google.common.truth.Truth.assertThat
import com.softdread.widgets.data.content.CategoryWeight
import com.softdread.widgets.data.content.CategoryWeighting
import com.softdread.widgets.data.content.Joke
import com.softdread.widgets.data.prefs.JokeCategory
import com.softdread.widgets.domain.model.Personality
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Test

/**
 * Daily joke seeding, category weighting and metadata generation.
 */
class JokeLogicTest {

    private val jokes = listOf(
        Joke("J001", "DAD_WORDPLAY", "Dad one"),
        Joke("J025", "NERD_TECH", "Nerd one"),
        Joke("J049", "WORK_LIFE", "Work one"),
        Joke("J073", "ANTI_JOKE", "Anti one"),
        Joke("J097", "ABSURD", "Absurd one"),
    )

    private val weights = listOf(
        CategoryWeighting(
            "CHAOTIC",
            listOf(
                CategoryWeight("DAD_WORDPLAY", 10),
                CategoryWeight("NERD_TECH", 25),
                CategoryWeight("WORK_LIFE", 10),
                CategoryWeight("ANTI_JOKE", 10),
                CategoryWeight("ABSURD", 45),
            ),
        ),
    )

    @Test
    fun `the daily seed is stable for a date and instance`() {
        val date = LocalDate.of(2026, 6, 15)
        assertThat(JokeLogic.dailySeed(date, "joke#1")).isEqualTo(JokeLogic.dailySeed(date, "joke#1"))
    }

    @Test
    fun `two instances on the same day get different seeds`() {
        val date = LocalDate.of(2026, 6, 15)
        assertThat(JokeLogic.dailySeed(date, "joke#1")).isNotEqualTo(JokeLogic.dailySeed(date, "joke#2"))
    }

    @Test
    fun `the seed advances with the date`() {
        assertThat(JokeLogic.dailySeed(LocalDate.of(2026, 6, 15), "joke#1"))
            .isNotEqualTo(JokeLogic.dailySeed(LocalDate.of(2026, 6, 16), "joke#1"))
    }

    @Test
    fun `category filtering narrows the library`() {
        val filtered = JokeLogic.eligibleJokes(jokes, setOf(JokeCategory.ABSURD, JokeCategory.NERD_TECH))
        assertThat(filtered.map { it.id }).containsExactly("J025", "J097")
    }

    @Test
    fun `filtering to nothing falls back to the whole library`() {
        val filtered = JokeLogic.eligibleJokes(jokes, emptySet())
        assertThat(filtered).hasSize(5)
    }

    @Test
    fun `category weighting follows the Bible distribution`() {
        val random = Random(11)
        val available = jokes.mapTo(mutableSetOf()) { it.category }
        val counts = mutableMapOf<String, Int>()
        repeat(20_000) {
            val category = JokeLogic.chooseCategory(weights, Personality.CHAOTIC, available, random)!!
            counts[category] = (counts[category] ?: 0) + 1
        }
        assertThat(counts.getValue("ABSURD") / 20_000.0).isWithin(0.02).of(0.45)
        assertThat(counts.getValue("NERD_TECH") / 20_000.0).isWithin(0.02).of(0.25)
        assertThat(counts.getValue("DAD_WORDPLAY") / 20_000.0).isWithin(0.02).of(0.10)
    }

    @Test
    fun `an unweighted personality still selects from the available categories`() {
        val available = jokes.mapTo(mutableSetOf()) { it.category }
        val category = JokeLogic.chooseCategory(weights, Personality.NEUTRAL, available, Random(1))
        assertThat(available).contains(category)
    }

    @Test
    fun `weighting never selects a category with no jokes left`() {
        val available = setOf("ABSURD")
        repeat(100) { index ->
            val category = JokeLogic.chooseCategory(weights, Personality.CHAOTIC, available, Random(index.toLong()))
            assertThat(category).isEqualTo("ABSURD")
        }
    }

    @Test
    fun `metadata values stay inside the Bible's ranges and are deterministic`() {
        val date = LocalDate.of(2026, 6, 15)
        val seed = JokeLogic.dailySeed(date, "joke#1")
        val first = JokeLogic.metadata(seed, date, "DAD_WORDPLAY")
        val second = JokeLogic.metadata(seed, date, "DAD_WORDPLAY")

        assertThat(first).isEqualTo(second)
        assertThat(first.score).isIn(1..10)
        assertThat(first.percent).isIn(65..99)
        assertThat(first.dayOfYear).isEqualTo(date.dayOfYear)
        assertThat(first.category).isEqualTo("dad / wordplay")
    }

    @Test
    fun `day of year covers a leap year`() {
        val leapDay = LocalDate.of(2028, 12, 31)
        val metadata = JokeLogic.metadata(1L, leapDay, "ABSURD")
        assertThat(metadata.dayOfYear).isEqualTo(366)
    }

    @Test
    fun `metadata variables are all present`() {
        val date = LocalDate.of(2026, 6, 15)
        val values = JokeLogic.metadata(1L, date, "ABSURD")
        val variables = JokeLogic.metadataVariables(values)
        assertThat(variables.keys).containsExactly("score", "percent", "category", "day_of_year")
        variables.values.forEach { assertThat(it).isNotEmpty() }
    }
}
