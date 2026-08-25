package com.softdread.widgets.domain.selection

import kotlinx.serialization.Serializable

/**
 * One recorded selection: which response was shown, when, and for which period.
 *
 * [periodKey] supports the Content Bible's Time Progress rule ("a Week response
 * shown this week is excluded next week") — it stores the identity of the period
 * the response was shown for, so adjacency can be evaluated without dates.
 */
@Serializable
data class HistoryEntry(
    val responseId: String,
    val atEpochMillis: Long,
    val periodKey: String? = null,
)

/** Recorded selections for one pool, most recent last. */
@Serializable
data class PoolHistory(val entries: List<HistoryEntry> = emptyList()) {

    fun recordedIds(): Set<String> = entries.mapTo(mutableSetOf()) { it.responseId }

    fun mostRecentIds(count: Int): Set<String> =
        entries.takeLast(count).mapTo(mutableSetOf()) { it.responseId }

    fun idsSince(epochMillis: Long): Set<String> =
        entries.filter { it.atEpochMillis >= epochMillis }.mapTo(mutableSetOf()) { it.responseId }

    fun idsForPeriods(periodKeys: Set<String>): Set<String> =
        entries.filter { it.periodKey != null && it.periodKey in periodKeys }
            .mapTo(mutableSetOf()) { it.responseId }

    fun plus(entry: HistoryEntry, maxEntries: Int): PoolHistory =
        PoolHistory((entries + entry).takeLast(maxEntries))

    fun cleared(): PoolHistory = PoolHistory(emptyList())
}

/**
 * The anti-repeat rules for one content pool, expressed as data rather than as
 * `when` branches inside each widget. Every rule below is a direct transcription
 * of the Content Bible's "Global anti-repeat policy" table plus the per-widget
 * notes; [AntiRepeatPolicies] holds the concrete values.
 */
data class AntiRepeatPolicy(
    /** Exclude the last N response IDs shown from this pool. */
    val recentCount: Int = 0,
    /** Exclude responses shown within this many days. */
    val cooldownDays: Int = 0,
    /** Exclude responses shown for the immediately adjacent period. */
    val excludeAdjacentPeriods: Boolean = false,
    /**
     * Prefer never-seen responses until this fraction of the pool has been seen.
     * The Bible's daily-content rule: "do not repeat until at least 80% of the
     * eligible pool has been seen".
     */
    val preferUnseenUntilCoverage: Float = 0f,
    /** Upper bound on retained entries, so history cannot grow without limit. */
    val maxHistoryEntries: Int = 64,
) {
    init {
        require(recentCount >= 0) { "recentCount must not be negative" }
        require(cooldownDays >= 0) { "cooldownDays must not be negative" }
        require(preferUnseenUntilCoverage in 0f..1f) { "coverage must be a fraction" }
    }
}

/**
 * Concrete policies, one per pool family.
 *
 * Source: Content Bible section 1, "Global anti-repeat policy".
 */
object AntiRepeatPolicies {

    /** "Keep the last 4 response IDs per trigger/state and exclude them." */
    val STATUS = AntiRepeatPolicy(recentCount = 4, maxHistoryEntries = 32)

    /** Jokes: hard cooldown 90 days, prefer unseen until 80% of the pool is seen. */
    val DAILY_JOKE = AntiRepeatPolicy(
        cooldownDays = 90,
        preferUnseenUntilCoverage = 0.8f,
        maxHistoryEntries = 130,
    )

    /** Joke metadata line: a small pool, so only immediate repeats are blocked. */
    val JOKE_METADATA = AntiRepeatPolicy(recentCount = 3, maxHistoryEntries = 24)

    /** "Do not reuse the same commentary response for 5 days." */
    val SCREEN_TIME_COMMENTARY = AntiRepeatPolicy(
        cooldownDays = 5,
        preferUnseenUntilCoverage = 0.8f,
        maxHistoryEntries = 48,
    )

    /** "Do not reuse the same equivalency for 7 days." */
    val SCREEN_TIME_EQUIVALENCY = AntiRepeatPolicy(cooldownDays = 7, maxHistoryEntries = 64)

    /** Equivalency framing template: rotates with the commentary. */
    val SCREEN_TIME_FRAMING = AntiRepeatPolicy(recentCount = 3, maxHistoryEntries = 24)

    /** "Do not reuse the same primary phrase for the same condition within 3 days." */
    val WEATHER = AntiRepeatPolicy(cooldownDays = 3, maxHistoryEntries = 48)

    /** "Do not reuse the same vibe response within 5 days." */
    val DAY_VIBE = AntiRepeatPolicy(cooldownDays = 5, maxHistoryEntries = 48)

    /** "Special-condition overlays have a 3-day cooldown." */
    val DAY_VIBE_SPECIAL = AntiRepeatPolicy(cooldownDays = 3, maxHistoryEntries = 48)

    /** "Do not reuse the same framing line on consecutive updates." */
    val COUNTDOWN = AntiRepeatPolicy(recentCount = 1, cooldownDays = 1, maxHistoryEntries = 32)

    /** "Do not repeat a response in adjacent periods." */
    val TIME_PROGRESS = AntiRepeatPolicy(
        recentCount = 1,
        excludeAdjacentPeriods = true,
        maxHistoryEntries = 32,
    )

    /** "Never return the exact same answer within the previous 12 taps." */
    val MAGIC_8_BALL = AntiRepeatPolicy(recentCount = 12, maxHistoryEntries = 48)
}
