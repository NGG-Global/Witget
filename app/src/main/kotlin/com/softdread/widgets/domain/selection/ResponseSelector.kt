package com.softdread.widgets.domain.selection

import com.softdread.widgets.data.content.ContentResponse
import kotlin.random.Random

/**
 * One request to pick a response from a pool.
 *
 * @property candidates the personality- and state-specific pool.
 * @property variables runtime values available for interpolation.
 * @property history what this pool has already shown for this widget instance.
 * @property policy the anti-repeat rules that apply to this pool.
 * @property seed deterministic seed, or `null` for a genuinely random pick.
 * @property maxChars copy budget for the current breakpoint, or `null` for none.
 * @property periodKey identity of the period this selection is for. When
 *   [reuseWithinPeriod] is set, a response already recorded for the same period
 *   is returned unchanged, which is what makes "daily" content stable across
 *   widget redraws.
 * @property adjacentPeriodKeys periods whose responses must not repeat here.
 * @property reuseWithinPeriod see [periodKey].
 */
data class SelectionRequest(
    val candidates: List<ContentResponse>,
    val variables: Map<String, String> = emptyMap(),
    val history: PoolHistory = PoolHistory(),
    val policy: AntiRepeatPolicy = AntiRepeatPolicy(),
    val seed: Long? = null,
    val maxChars: Int? = null,
    val periodKey: String? = null,
    val adjacentPeriodKeys: Set<String> = emptySet(),
    val reuseWithinPeriod: Boolean = false,
    val nowEpochMillis: Long = 0L,
)

/** The chosen response, its rendered text, and the history to persist. */
data class Selection(
    val response: ContentResponse,
    val text: String,
    val history: PoolHistory,
    val wasReused: Boolean = false,
)

/**
 * The single response-selection engine used by all eight widgets.
 *
 * Selection is a filter chain, applied in this order:
 *
 *  1. drop responses whose required variables cannot be resolved, so an
 *     unresolved `{placeholder}` can never reach the user;
 *  2. drop responses that overflow the breakpoint's copy budget — the Content
 *     Bible requires selecting a shorter eligible string rather than truncating
 *     a punchline;
 *  3. drop responses excluded by the anti-repeat policy;
 *  4. if a coverage rule applies, prefer responses this instance has never seen.
 *
 * Each step is relaxed in reverse order if it would leave nothing to show, so
 * the engine always returns copy when the pool is non-empty.
 */
class ResponseSelector {

    fun select(request: SelectionRequest): Selection? {
        val resolvable = request.candidates.filter {
            Interpolation.canResolve(it.text, request.variables)
        }
        if (resolvable.isEmpty()) return null

        if (request.reuseWithinPeriod && request.periodKey != null) {
            val recorded = request.history.entries.lastOrNull { it.periodKey == request.periodKey }
            val reused = recorded?.let { entry -> resolvable.firstOrNull { it.id == entry.responseId } }
            if (reused != null) {
                return Selection(
                    response = reused,
                    text = Interpolation.interpolate(reused.text, request.variables),
                    history = request.history,
                    wasReused = true,
                )
            }
        }

        val withinBudget = applyBudget(resolvable, request)
        val eligible = applyAntiRepeat(withinBudget, request)
        val chosen = pick(eligible, request) ?: return null

        val entry = HistoryEntry(
            responseId = chosen.id,
            atEpochMillis = request.nowEpochMillis,
            periodKey = request.periodKey,
        )
        return Selection(
            response = chosen,
            text = Interpolation.interpolate(chosen.text, request.variables),
            history = request.history.plus(entry, request.policy.maxHistoryEntries),
        )
    }

    /**
     * Keeps responses that fit the breakpoint's character budget after
     * interpolation. If nothing fits, falls back to the shortest available
     * responses rather than truncating one.
     */
    private fun applyBudget(
        candidates: List<ContentResponse>,
        request: SelectionRequest,
    ): List<ContentResponse> {
        val budget = request.maxChars ?: return candidates
        val rendered = candidates.associateWith {
            Interpolation.interpolate(it.text, request.variables).length
        }
        val fitting = candidates.filter { (rendered[it] ?: 0) <= budget }
        if (fitting.isNotEmpty()) return fitting
        val shortest = rendered.values.minOrNull() ?: return candidates
        return candidates.filter { (rendered[it] ?: 0) == shortest }
    }

    /**
     * Applies the pool's [AntiRepeatPolicy], relaxing rules one at a time if the
     * pool would otherwise be exhausted. The Bible's instruction is to "reset
     * history only if the eligible pool is exhausted", which is what the
     * progressive relaxation below implements.
     */
    private fun applyAntiRepeat(
        candidates: List<ContentResponse>,
        request: SelectionRequest,
    ): List<ContentResponse> {
        if (candidates.size <= 1) return candidates
        val policy = request.policy
        val history = request.history

        val blockedByAdjacency = if (policy.excludeAdjacentPeriods && request.adjacentPeriodKeys.isNotEmpty()) {
            history.idsForPeriods(request.adjacentPeriodKeys)
        } else {
            emptySet()
        }
        val blockedByCooldown = if (policy.cooldownDays > 0) {
            val cutoff = request.nowEpochMillis - policy.cooldownDays.toLong() * MILLIS_PER_DAY
            history.idsSince(cutoff)
        } else {
            emptySet()
        }
        val blockedByRecency = if (policy.recentCount > 0) {
            history.mostRecentIds(policy.recentCount)
        } else {
            emptySet()
        }

        // Strictest first, then progressively drop the softest rule.
        val attempts = listOf(
            blockedByAdjacency + blockedByCooldown + blockedByRecency,
            blockedByAdjacency + blockedByRecency,
            blockedByAdjacency + history.mostRecentIds(1),
            history.mostRecentIds(1),
            emptySet(),
        )
        val allowed = attempts.firstNotNullOfOrNull { blocked ->
            candidates.filterNot { it.id in blocked }.takeIf { it.isNotEmpty() }
        } ?: candidates

        if (policy.preferUnseenUntilCoverage > 0f) {
            val seen = history.recordedIds()
            val coverage = if (candidates.isEmpty()) 1f else seen.count { id ->
                candidates.any { it.id == id }
            }.toFloat() / candidates.size
            if (coverage < policy.preferUnseenUntilCoverage) {
                val unseen = allowed.filterNot { it.id in seen }
                if (unseen.isNotEmpty()) return unseen
            }
        }
        return allowed
    }

    private fun pick(candidates: List<ContentResponse>, request: SelectionRequest): ContentResponse? {
        if (candidates.isEmpty()) return null
        val random = request.seed?.let { Random(it) } ?: Random.Default
        return candidates[random.nextInt(candidates.size)]
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}

/**
 * Weighted choice over a set of buckets, used for the two places the Content
 * Bible weights content: Magic 8 Ball sentiment (40/20/40) and Daily Joke
 * category weighting per personality.
 */
object WeightedChooser {

    fun <T> choose(weights: Map<T, Int>, random: Random): T? {
        val positive = weights.filterValues { it > 0 }
        if (positive.isEmpty()) return null
        val total = positive.values.sum()
        var target = random.nextInt(total)
        for ((key, weight) in positive) {
            target -= weight
            if (target < 0) return key
        }
        return positive.keys.last()
    }
}
