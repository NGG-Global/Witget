package com.softdread.widgets.widgets

import com.google.common.truth.Truth.assertThat
import com.softdread.widgets.data.content.ContentResponse
import com.softdread.widgets.domain.selection.AntiRepeatPolicies
import com.softdread.widgets.widgets.common.ContentSession
import org.junit.Test

/**
 * The Content Bible's rule for frequently changing status widgets is "keep the
 * last 4 response IDs per trigger/state and exclude them from selection".
 *
 * One widget update builds every breakpoint, so a naive session wrote six
 * entries per refresh. Four of those were for tiles nobody was looking at, and
 * the one that *was* shown — selected first, for the largest size — dropped out
 * of a four-deep window inside a single update, which let it come back on the
 * very next refresh. The session now keeps one entry per pool per update: the
 * first, which [com.softdread.widgets.widgets.common.SoftDreadWidget] orders to
 * be the breakpoint on screen.
 */
class ContentSessionTest {

    private val pool = (1..10).map { ContentResponse(id = "R%02d".format(it), text = "line $it") }

    private fun select(session: ContentSession) = session.select(
        poolKey = "battery:B6",
        candidates = pool,
        policy = AntiRepeatPolicies.STATUS,
    )

    @Test
    fun `one update records one entry per pool however many breakpoints it builds`() {
        val session = ContentSession(emptyMap(), nowEpochMillis = 0L)
        repeat(6) { select(session) }

        val recorded = session.historyUpdates.getValue("battery:B6")
        assertThat(recorded.entries).hasSize(1)
    }

    @Test
    fun `the recorded entry is the one selected first`() {
        val session = ContentSession(emptyMap(), nowEpochMillis = 0L)
        val shown = select(session)!!.response.id
        repeat(5) { select(session) }

        val recorded = session.historyUpdates.getValue("battery:B6")
        assertThat(recorded.entries.single().responseId).isEqualTo(shown)
    }

    @Test
    fun `later breakpoints in the same update still avoid the shown line`() {
        val session = ContentSession(emptyMap(), nowEpochMillis = 0L)
        val shown = select(session)!!.response.id
        val others = (1..5).map { select(session)!!.response.id }
        assertThat(others).doesNotContain(shown)
    }

    @Test
    fun `four updates fill the exclusion window the Bible specifies`() {
        var history = emptyMap<String, com.softdread.widgets.domain.selection.PoolHistory>()
        val shown = mutableListOf<String>()
        repeat(4) {
            val session = ContentSession(history, nowEpochMillis = 0L)
            shown += select(session)!!.response.id
            repeat(5) { select(session) }
            history = history + session.historyUpdates
        }
        // Four updates, four recorded IDs, all distinct: the window now holds
        // exactly the last four responses the user was shown.
        assertThat(history.getValue("battery:B6").entries).hasSize(4)
        assertThat(shown.toSet()).hasSize(4)
    }

    @Test
    fun `a non-response choice is recorded once per update too`() {
        val session = ContentSession(emptyMap(), nowEpochMillis = 0L)
        repeat(6) {
            session.record("screentime:units", "ST0$it", AntiRepeatPolicies.SCREEN_TIME_EQUIVALENCY)
        }
        assertThat(session.historyUpdates.getValue("screentime:units").entries).hasSize(1)
    }
}
