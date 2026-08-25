package com.softdread.widgets.domain.logic

import com.softdread.widgets.data.content.CategoryWeighting
import com.softdread.widgets.data.content.Joke
import com.softdread.widgets.data.prefs.JokeCategory
import com.softdread.widgets.domain.model.Personality
import com.softdread.widgets.domain.selection.WeightedChooser
import java.time.LocalDate
import kotlin.random.Random

/** Decorative metadata rendered under the joke on medium and large tiles. */
data class JokeMetadataValues(
    val score: Int,
    val percent: Int,
    val category: String,
    val dayOfYear: Int,
)

/**
 * Daily joke selection.
 *
 * The Bible's determinism rule is that the daily seed combines the local date
 * with the widget instance ID, so the joke does not change on every redraw and
 * two widgets on the same home screen can show different jokes. The metadata
 * numbers are generated from that same seed, so they stay stable through the day
 * too.
 */
object JokeLogic {

    const val METADATA_STATE_KEY = "J_META"

    /** Combines the local date and the instance key into a stable daily seed. */
    fun dailySeed(date: LocalDate, instanceKey: String): Long =
        date.toEpochDay() * 31L + instanceKey.hashCode().toLong()

    fun periodKey(date: LocalDate): String = "joke:$date"

    /**
     * Narrows the library to the user's enabled categories, then picks a
     * category using the personality's weighting before picking a joke.
     *
     * Weighting by category first (rather than weighting every joke) is what
     * makes the Bible's percentages actually hold: a 45%-Absurd chaotic mode
     * draws from Absurd 45% of the time regardless of how many Absurd jokes the
     * library happens to contain.
     */
    fun eligibleJokes(
        jokes: List<Joke>,
        enabledCategories: Set<JokeCategory>,
    ): List<Joke> {
        if (enabledCategories.isEmpty()) return jokes
        val keys = enabledCategories.mapTo(mutableSetOf()) { it.key }
        return jokes.filter { it.category in keys }.ifEmpty { jokes }
    }

    fun chooseCategory(
        weightings: List<CategoryWeighting>,
        personality: Personality,
        available: Set<String>,
        random: Random,
    ): String? {
        if (available.isEmpty()) return null
        val weights = weightings.firstOrNull { it.mode == personality.key }
            ?.weights
            ?.filter { it.category in available }
            ?.associate { it.category to it.weight }
            ?.filterValues { it > 0 }
            ?: return available.random(random)
        if (weights.isEmpty()) return available.random(random)
        return WeightedChooser.choose(weights, random)
    }

    /**
     * Decorative metadata values. `{score}` is 1-10, `{percent}` is 65-99 and
     * `{day_of_year}` is 1-366, exactly as the Bible specifies.
     */
    fun metadata(seed: Long, date: LocalDate, category: String): JokeMetadataValues {
        val random = Random(seed xor METADATA_SALT)
        return JokeMetadataValues(
            score = random.nextInt(1, 11),
            percent = random.nextInt(65, 100),
            category = humanCategory(category),
            dayOfYear = date.dayOfYear,
        )
    }

    fun metadataVariables(values: JokeMetadataValues): Map<String, String> = mapOf(
        "score" to values.score.toString(),
        "percent" to values.percent.toString(),
        "category" to values.category,
        "day_of_year" to values.dayOfYear.toString(),
    )

    fun humanCategory(key: String): String = when (key) {
        "DAD_WORDPLAY" -> "dad / wordplay"
        "NERD_TECH" -> "nerd / tech"
        "WORK_LIFE" -> "work / life"
        "ANTI_JOKE" -> "anti-joke / dry"
        "ABSURD" -> "absurd"
        else -> key.lowercase().replace('_', ' ')
    }

    private const val METADATA_SALT = 0x5EEDL
}
