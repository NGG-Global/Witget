package com.softdread.widgets.widgets.common

import com.softdread.widgets.data.content.ContentResponse
import com.softdread.widgets.domain.selection.AntiRepeatPolicy
import com.softdread.widgets.domain.selection.PoolHistory
import com.softdread.widgets.domain.selection.ResponseSelector
import com.softdread.widgets.domain.selection.Selection
import com.softdread.widgets.domain.selection.SelectionRequest

/**
 * One widget update's worth of response selection.
 *
 * The session threads anti-repeat history through every pool a widget touches
 * and accumulates the writes, so a widget that picks a band response, an
 * equivalency framing line and a metadata line performs one history read and one
 * history write rather than six.
 *
 * Selections made later in a session see the earlier ones, which is what stops a
 * widget rendered at two breakpoints in the same update from showing the same
 * string twice.
 */
class ContentSession(
    private val startingHistory: Map<String, PoolHistory>,
    private val nowEpochMillis: Long,
    private val selector: ResponseSelector = ResponseSelector(),
) {

    private val updates = mutableMapOf<String, PoolHistory>()

    /** History to persist after the update completes. */
    val historyUpdates: Map<String, PoolHistory> get() = updates.toMap()

    fun historyFor(poolKey: String): PoolHistory =
        updates[poolKey] ?: startingHistory[poolKey] ?: PoolHistory()

    fun select(
        poolKey: String,
        candidates: List<ContentResponse>,
        policy: AntiRepeatPolicy,
        variables: Map<String, String> = emptyMap(),
        maxChars: Int? = null,
        seed: Long? = null,
        periodKey: String? = null,
        adjacentPeriodKeys: Set<String> = emptySet(),
        reuseWithinPeriod: Boolean = false,
    ): Selection? {
        val selection = selector.select(
            SelectionRequest(
                candidates = candidates,
                variables = variables,
                history = historyFor(poolKey),
                policy = policy,
                seed = seed,
                maxChars = maxChars,
                periodKey = periodKey,
                adjacentPeriodKeys = adjacentPeriodKeys,
                reuseWithinPeriod = reuseWithinPeriod,
                nowEpochMillis = nowEpochMillis,
            ),
        )
        // First write per pool wins. One update builds every breakpoint, but the
        // user only ever sees one of them, and the Content Bible's rule is
        // "keep the last 4 response IDs per trigger/state and exclude them".
        // Recording all six pushed the shown line out of a four-deep window
        // within a single refresh, so the largest tiles could repeat themselves
        // on the very next update. The displayed breakpoint is selected first
        // (see SoftDreadWidget), so the entry kept here is the one shown; the
        // later breakpoints still see it and still pick something else.
        if (selection != null && !selection.wasReused && poolKey !in updates) {
            updates[poolKey] = selection.history
        }
        return selection
    }

    /** Records a non-response choice, such as which equivalency unit was used. */
    fun record(poolKey: String, id: String, policy: AntiRepeatPolicy, periodKey: String? = null) {
        if (poolKey in updates) return
        val current = historyFor(poolKey)
        updates[poolKey] = current.plus(
            com.softdread.widgets.domain.selection.HistoryEntry(id, nowEpochMillis, periodKey),
            policy.maxHistoryEntries,
        )
    }

    /** IDs excluded by [policy]'s cooldown, for pools selected outside the engine. */
    fun cooledDownIds(poolKey: String, policy: AntiRepeatPolicy): Set<String> {
        if (policy.cooldownDays <= 0) return historyFor(poolKey).mostRecentIds(policy.recentCount)
        val cutoff = nowEpochMillis - policy.cooldownDays.toLong() * 24L * 60L * 60L * 1000L
        return historyFor(poolKey).idsSince(cutoff)
    }
}
