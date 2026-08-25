package com.softdread.widgets.domain.logic

import com.softdread.widgets.domain.selection.WeightedChooser
import kotlin.random.Random

/** The three answer classes the Bible groups Magic 8 Ball responses into. */
enum class AnswerSentiment(val stateKey: String, val weight: Int) {
    POSITIVE("M8_POSITIVE", 40),
    NEUTRAL("M8_NEUTRAL", 20),
    NEGATIVE("M8_NEGATIVE", 40),
}

/**
 * Magic 8 Ball sentiment selection.
 *
 * The Bible sets a default weighting of 40% positive, 20% neutral, 40% negative
 * — "intentionally more mischievous than the classic toy while remaining
 * balanced" — and requires that outcomes never depend on the question text.
 */
object Magic8BallLogic {

    fun chooseSentiment(random: Random = Random.Default): AnswerSentiment =
        WeightedChooser.choose(
            AnswerSentiment.entries.associateWith { it.weight },
            random,
        ) ?: AnswerSentiment.NEUTRAL

    /**
     * Sentiments to try, most-weighted first, so a tap still produces an answer
     * when the chosen class is entirely blocked by the 12-tap repeat rule.
     */
    fun sentimentFallbackOrder(first: AnswerSentiment): List<AnswerSentiment> =
        listOf(first) + AnswerSentiment.entries.filterNot { it == first }
}
