package com.softdread.widgets.domain.selection

import com.google.common.truth.Truth.assertThat
import com.softdread.widgets.data.content.ContentResponse
import org.junit.Test

/**
 * The shared selection engine.
 *
 * These tests cover the guarantees every widget depends on: a response is never
 * shown with an unresolved placeholder, an immediate repeat is blocked while an
 * alternative exists, cooldowns and coverage rules are honoured, and the engine
 * still returns copy when every rule would otherwise exclude everything.
 */
class ResponseSelectorTest {

    private val selector = ResponseSelector()
    private val now = 1_700_000_000_000L
    private val day = 24L * 60L * 60L * 1000L

    private fun response(id: String, text: String) =
        ContentResponse(id, text, Interpolation.variablesIn(text).toList())

    private val pool = listOf(
        response("A", "Alpha {percent}%"),
        response("B", "Bravo {percent}%"),
        response("C", "Charlie {percent}%"),
        response("D", "Delta {percent}%"),
    )

    private fun request(
        candidates: List<ContentResponse> = pool,
        variables: Map<String, String> = mapOf("percent" to "23"),
        history: PoolHistory = PoolHistory(),
        policy: AntiRepeatPolicy = AntiRepeatPolicy(),
        seed: Long? = null,
        maxChars: Int? = null,
        periodKey: String? = null,
        adjacent: Set<String> = emptySet(),
        reuseWithinPeriod: Boolean = false,
    ) = SelectionRequest(
        candidates = candidates,
        variables = variables,
        history = history,
        policy = policy,
        seed = seed,
        maxChars = maxChars,
        periodKey = periodKey,
        adjacentPeriodKeys = adjacent,
        reuseWithinPeriod = reuseWithinPeriod,
        nowEpochMillis = now,
    )

    @Test
    fun `variables are interpolated into the chosen response`() {
        val selection = selector.select(request(seed = 1))
        assertThat(selection).isNotNull()
        assertThat(selection!!.text).contains("23%")
        assertThat(selection.text).doesNotContain("{")
    }

    @Test
    fun `responses whose variables cannot resolve are never chosen`() {
        val mixed = pool + response("E", "Echo {missing_variable}")
        repeat(50) { index ->
            val selection = selector.select(request(candidates = mixed, seed = index.toLong()))
            assertThat(selection!!.response.id).isNotEqualTo("E")
        }
    }

    @Test
    fun `a pool with no resolvable response returns null rather than a broken string`() {
        val unresolvable = listOf(response("X", "Value {missing}"))
        assertThat(selector.select(request(candidates = unresolvable))).isNull()
    }

    @Test
    fun `output never contains an unresolved placeholder`() {
        // Every response in the library goes through Interpolation, which strips
        // anything left over as a last line of defence.
        val text = Interpolation.interpolate("Battery {percent}% and {unknown} rising", mapOf("percent" to "40"))
        assertThat(text).doesNotContain("{")
        assertThat(text).doesNotContain("}")
        assertThat(text).contains("40%")
    }

    @Test
    fun `interpolation tidies the gap a removed variable leaves behind`() {
        assertThat(Interpolation.interpolate("{a} today", emptyMap())).isEqualTo("today")
        assertThat(Interpolation.interpolate("Usage {a}, fine", emptyMap())).isEqualTo("Usage, fine")
    }

    @Test
    fun `the most recent response is not repeated immediately`() {
        val history = PoolHistory(listOf(HistoryEntry("A", now - 1000)))
        repeat(50) { index ->
            val selection = selector.select(
                request(history = history, policy = AntiRepeatPolicy(recentCount = 1), seed = index.toLong()),
            )
            assertThat(selection!!.response.id).isNotEqualTo("A")
        }
    }

    @Test
    fun `the last four responses are excluded for status widgets`() {
        val history = PoolHistory(
            listOf("A", "B", "C").mapIndexed { index, id -> HistoryEntry(id, now - index * 1000L) },
        )
        repeat(30) { index ->
            val selection = selector.select(
                request(history = history, policy = AntiRepeatPolicies.STATUS, seed = index.toLong()),
            )
            assertThat(selection!!.response.id).isEqualTo("D")
        }
    }

    @Test
    fun `history is reset only when the eligible pool is exhausted`() {
        // All four already shown; the Bible allows recycling at that point, but
        // never the single most recent one while an alternative exists.
        val history = PoolHistory(
            pool.mapIndexed { index, item -> HistoryEntry(item.id, now - index * 1000L) },
        )
        val selection = selector.select(
            request(history = history, policy = AntiRepeatPolicies.STATUS, seed = 5),
        )
        assertThat(selection).isNotNull()
        assertThat(selection!!.response.id).isNotEqualTo("A")
    }

    @Test
    fun `a cooldown excludes responses seen inside the window`() {
        val history = PoolHistory(
            listOf(
                HistoryEntry("A", now - 2 * day),
                HistoryEntry("B", now - 10 * day),
            ),
        )
        repeat(30) { index ->
            val selection = selector.select(
                request(
                    history = history,
                    policy = AntiRepeatPolicy(cooldownDays = 5),
                    seed = index.toLong(),
                ),
            )
            assertThat(selection!!.response.id).isNotEqualTo("A")
        }
    }

    @Test
    fun `unseen responses are preferred until coverage is reached`() {
        val history = PoolHistory(listOf(HistoryEntry("A", now - 100 * day)))
        repeat(30) { index ->
            val selection = selector.select(
                request(
                    history = history,
                    policy = AntiRepeatPolicy(preferUnseenUntilCoverage = 0.8f),
                    seed = index.toLong(),
                ),
            )
            assertThat(selection!!.response.id).isNotEqualTo("A")
        }
    }

    @Test
    fun `adjacent period responses are excluded`() {
        val history = PoolHistory(listOf(HistoryEntry("A", now - day, periodKey = "week:35")))
        repeat(30) { index ->
            val selection = selector.select(
                request(
                    history = history,
                    policy = AntiRepeatPolicies.TIME_PROGRESS,
                    periodKey = "week:36",
                    adjacent = setOf("week:35"),
                    seed = index.toLong(),
                ),
            )
            assertThat(selection!!.response.id).isNotEqualTo("A")
        }
    }

    @Test
    fun `daily content is stable across redraws within the same period`() {
        val first = selector.select(request(periodKey = "2026-06-15", reuseWithinPeriod = true, seed = 42))!!
        var history = first.history
        repeat(20) {
            val again = selector.select(
                request(history = history, periodKey = "2026-06-15", reuseWithinPeriod = true, seed = 42),
            )!!
            assertThat(again.response.id).isEqualTo(first.response.id)
            assertThat(again.wasReused).isTrue()
            history = again.history
        }
    }

    @Test
    fun `a new period draws fresh content`() {
        val first = selector.select(request(periodKey = "2026-06-15", reuseWithinPeriod = true, seed = 42))!!
        val next = selector.select(
            request(
                history = first.history,
                policy = AntiRepeatPolicy(recentCount = 1),
                periodKey = "2026-06-16",
                reuseWithinPeriod = true,
                seed = 43,
            ),
        )!!
        assertThat(next.wasReused).isFalse()
        assertThat(next.response.id).isNotEqualTo(first.response.id)
    }

    @Test
    fun `the same seed always picks the same response`() {
        val a = selector.select(request(seed = 99))!!
        val b = selector.select(request(seed = 99))!!
        assertThat(a.response.id).isEqualTo(b.response.id)
    }

    @Test
    fun `a copy budget selects a shorter eligible string rather than truncating`() {
        val budgeted = listOf(
            response("LONG", "A very long response that will not fit inside a compact tile at all"),
            response("SHORT", "Short."),
        )
        val selection = selector.select(request(candidates = budgeted, maxChars = 20, seed = 3))
        assertThat(selection!!.response.id).isEqualTo("SHORT")
        assertThat(selection.text).isEqualTo("Short.")
    }

    @Test
    fun `when nothing fits the budget the shortest is used and never cut`() {
        val budgeted = listOf(
            response("A", "Still quite a long line here"),
            response("B", "Also long but slightly less"),
        )
        val selection = selector.select(request(candidates = budgeted, maxChars = 5, seed = 3))!!
        assertThat(selection.text).isEqualTo("Also long but slightly less")
    }

    @Test
    fun `history is bounded by the policy`() {
        var history = PoolHistory()
        val policy = AntiRepeatPolicy(maxHistoryEntries = 5)
        repeat(50) { index ->
            history = selector.select(request(history = history, policy = policy, seed = index.toLong()))!!.history
        }
        assertThat(history.entries).hasSize(5)
    }

    @Test
    fun `weighted choice respects the configured distribution`() {
        val weights = mapOf("positive" to 40, "neutral" to 20, "negative" to 40)
        val random = kotlin.random.Random(17)
        val counts = mutableMapOf<String, Int>()
        repeat(20_000) {
            val choice = WeightedChooser.choose(weights, random)!!
            counts[choice] = (counts[choice] ?: 0) + 1
        }
        assertThat(counts.getValue("positive") / 20_000.0).isWithin(0.02).of(0.40)
        assertThat(counts.getValue("neutral") / 20_000.0).isWithin(0.02).of(0.20)
        assertThat(counts.getValue("negative") / 20_000.0).isWithin(0.02).of(0.40)
    }

    @Test
    fun `zero weight buckets are never chosen`() {
        val weights = mapOf("a" to 0, "b" to 5)
        val random = kotlin.random.Random(1)
        repeat(200) { assertThat(WeightedChooser.choose(weights, random)).isEqualTo("b") }
    }
}
