package com.softdread.widgets.domain.logic

import com.google.common.truth.Truth.assertThat
import com.softdread.widgets.data.content.ContentResponse
import com.softdread.widgets.domain.selection.AntiRepeatPolicies
import com.softdread.widgets.domain.selection.PoolHistory
import com.softdread.widgets.domain.selection.ResponseSelector
import com.softdread.widgets.domain.selection.SelectionRequest
import kotlin.random.Random
import org.junit.Test

/**
 * Magic 8 Ball sentiment weighting and the 12-tap repeat rule.
 */
class Magic8BallLogicTest {

    @Test
    fun `sentiment weighting matches the Bible's 40-20-40 split`() {
        val random = Random(23)
        val counts = mutableMapOf<AnswerSentiment, Int>()
        repeat(20_000) {
            val sentiment = Magic8BallLogic.chooseSentiment(random)
            counts[sentiment] = (counts[sentiment] ?: 0) + 1
        }
        assertThat(counts.getValue(AnswerSentiment.POSITIVE) / 20_000.0).isWithin(0.02).of(0.40)
        assertThat(counts.getValue(AnswerSentiment.NEUTRAL) / 20_000.0).isWithin(0.02).of(0.20)
        assertThat(counts.getValue(AnswerSentiment.NEGATIVE) / 20_000.0).isWithin(0.02).of(0.40)
    }

    @Test
    fun `the fallback order starts with the chosen sentiment and covers the rest`() {
        AnswerSentiment.entries.forEach { sentiment ->
            val order = Magic8BallLogic.sentimentFallbackOrder(sentiment)
            assertThat(order.first()).isEqualTo(sentiment)
            assertThat(order).containsExactlyElementsIn(AnswerSentiment.entries)
        }
    }

    @Test
    fun `each sentiment maps to its Bible state key`() {
        assertThat(AnswerSentiment.POSITIVE.stateKey).isEqualTo("M8_POSITIVE")
        assertThat(AnswerSentiment.NEUTRAL.stateKey).isEqualTo("M8_NEUTRAL")
        assertThat(AnswerSentiment.NEGATIVE.stateKey).isEqualTo("M8_NEGATIVE")
    }

    @Test
    fun `an answer is never repeated within twelve taps`() {
        val selector = ResponseSelector()
        val answers = (1..30).map { ContentResponse("A%02d".format(it), "Answer $it") }
        var history = PoolHistory()
        val shown = mutableListOf<String>()

        repeat(40) { tap ->
            val selection = selector.select(
                SelectionRequest(
                    candidates = answers,
                    history = history,
                    policy = AntiRepeatPolicies.MAGIC_8_BALL,
                    nowEpochMillis = 1_700_000_000_000L + tap * 1000L,
                ),
            )!!
            // The previous twelve answers must not contain this one.
            assertThat(shown.takeLast(12)).doesNotContain(selection.response.id)
            shown += selection.response.id
            history = selection.history
        }
    }

    @Test
    fun `a small pool still answers rather than going silent`() {
        val selector = ResponseSelector()
        val answers = listOf(ContentResponse("ONLY", "The only answer."))
        var history = PoolHistory()
        repeat(20) {
            val selection = selector.select(
                SelectionRequest(
                    candidates = answers,
                    history = history,
                    policy = AntiRepeatPolicies.MAGIC_8_BALL,
                    nowEpochMillis = 1_700_000_000_000L,
                ),
            )
            assertThat(selection).isNotNull()
            history = selection!!.history
        }
    }
}
