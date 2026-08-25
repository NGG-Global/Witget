package com.softdread.widgets.widgets.joke

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.softdread.widgets.data.content.ContentDocument
import com.softdread.widgets.data.content.ContentResponse
import com.softdread.widgets.data.content.Joke
import com.softdread.widgets.data.content.pool
import com.softdread.widgets.domain.logic.JokeLogic
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.domain.selection.AntiRepeatPolicies
import com.softdread.widgets.domain.selection.PoolHistory
import com.softdread.widgets.widgets.common.SoftDreadWidget
import com.softdread.widgets.widgets.common.TileChip
import com.softdread.widgets.widgets.common.TileContent
import com.softdread.widgets.widgets.common.WidgetEnvironment
import com.softdread.widgets.widgets.common.WidgetPayload
import com.softdread.widgets.widgets.common.openAppAction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * Daily Joke.
 *
 * One joke per local day per widget instance. The joke is chosen from a seed
 * built out of the date and the instance key, and — more importantly — the
 * chosen ID is recorded against that day, so every subsequent redraw returns the
 * same joke rather than re-rolling. Two Daily Joke widgets on one home screen
 * still show different jokes, because the instance key is part of the seed.
 *
 * The 90-day hard cooldown and the "prefer unseen until 80% of the pool has been
 * seen" rule are enforced by the shared selector.
 */
class DailyJokeWidget : SoftDreadWidget(WidgetType.DAILY_JOKE) {

    override suspend fun buildPayload(environment: WidgetEnvironment): WidgetPayload {
        val document = environment.content.document(WidgetType.DAILY_JOKE)
        // LocalDate.ofInstant is API 34; atZone().toLocalDate() is equivalent
        // and available from API 26, which is this app's minimum.
        val today: LocalDate = environment.clock.now().atZone(environment.clock.zone()).toLocalDate()
        val seed = JokeLogic.dailySeed(today, environment.config.instanceKey)
        val periodKey = JokeLogic.periodKey(today)

        val joke = pickJoke(document, environment, seed, periodKey)
        val metadataValues = JokeLogic.metadata(seed, today, joke.category)
        val metadataVariables = JokeLogic.metadataVariables(metadataValues)
        val metadataCandidates = document.pool(JokeLogic.METADATA_STATE_KEY, environment.personality)

        val content = environment.breakpoints.associateWith { breakpoint ->
            val chips = if (breakpoint.showsCircle) {
                metadataChips(environment, metadataCandidates, metadataVariables, if (breakpoint.isLarge) 2 else 1)
            } else {
                emptyList()
            }
            TileContent(
                label = "daily joke",
                labelDetail = if (breakpoint.isLarge) today.format(DAY_FORMAT) else null,
                voice = joke.text,
                chips = chips,
                contentDescription = buildString {
                    append(joke.text)
                    chips.firstOrNull()?.let { append(". ${it.text}") }
                },
            )
        }
        return WidgetPayload(
            content,
            onClick = openAppAction(environment.context, WidgetType.DAILY_JOKE, environment.config.appWidgetId),
        )
    }

    /**
     * Picks today's joke: reuse the one already recorded for this date, or draw a
     * fresh one by weighting the personality's category preferences before
     * applying the pool's cooldown rules.
     */
    private fun pickJoke(
        document: ContentDocument,
        environment: WidgetEnvironment,
        seed: Long,
        periodKey: String,
    ): Joke {
        val eligible = JokeLogic.eligibleJokes(document.jokes, environment.config.jokeCategories)
        val history: PoolHistory = environment.session.historyFor(JOKE_POOL)

        history.entries.lastOrNull { it.periodKey == periodKey }?.let { recorded ->
            eligible.firstOrNull { it.id == recorded.responseId }?.let { return it }
        }

        val random = Random(seed)
        val available = eligible.mapTo(mutableSetOf()) { it.category }
        val category = JokeLogic.chooseCategory(document.categoryWeights, environment.personality, available, random)
        val inCategory = eligible.filter { it.category == category }.ifEmpty { eligible }

        // Reuse the shared selector so the 90-day cooldown and unseen-preference
        // rules behave identically to every other pool in the app.
        val asResponses = inCategory.map { ContentResponse(id = it.id, text = it.text) }
        val selection = environment.session.select(
            poolKey = JOKE_POOL,
            candidates = asResponses,
            policy = AntiRepeatPolicies.DAILY_JOKE,
            seed = seed,
            periodKey = periodKey,
            reuseWithinPeriod = true,
        )
        return inCategory.firstOrNull { it.id == selection?.response?.id } ?: inCategory.first()
    }

    private fun metadataChips(
        environment: WidgetEnvironment,
        candidates: List<ContentResponse>,
        variables: Map<String, String>,
        count: Int,
    ): List<TileChip> = buildList {
        repeat(count) { index ->
            environment.session.select(
                poolKey = "joke:meta",
                candidates = candidates,
                policy = AntiRepeatPolicies.JOKE_METADATA,
                variables = variables,
                maxChars = 28,
            )?.let { add(TileChip(it.text.lowercase(), emphasised = index > 0)) }
        }
    }

    private companion object {
        const val JOKE_POOL = "joke:library"
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
    }
}

class DailyJokeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DailyJokeWidget()
}
