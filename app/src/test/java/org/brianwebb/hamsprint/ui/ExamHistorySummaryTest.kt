package org.brianwebb.hamsprint.ui

import org.brianwebb.hamsprint.data.ExamRecord
import org.brianwebb.hamsprint.ui.exam.ExamHistorySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamHistorySummaryTest {
    private fun exams(vararg scoresNewestFirst: Int) =
        scoresNewestFirst.mapIndexed { i, s -> ExamRecord(1000L - i, s, 35, 26, 60_000) }

    private fun summary(pass: Double, vararg scores: Int) = ExamHistorySummary(exams(*scores), 35, 26, pass)

    @Test
    fun `no attempts means no stats and not exam ready`() {
        val s = summary(0.99)
        assertNull(s.recentAverage)
        assertNull(s.best)
        assertEquals(0, s.passStreak)
        assertFalse(s.examReady)
    }

    @Test
    fun `streak counts consecutive passes from the newest attempt`() {
        assertEquals(2, summary(0.5, 30, 27, 20, 31).passStreak)
        assertEquals(0, summary(0.5, 20, 30, 31).passStreak)
    }

    @Test
    fun `exam ready needs both the memory model and two passes in a row`() {
        assertTrue(summary(0.97, 30, 28).examReady)
        assertFalse("model not there yet", summary(0.80, 30, 28).examReady)
        assertFalse("latest exam failed", summary(0.97, 25, 30, 31).examReady)
        assertFalse("only one pass", summary(0.97, 30).examReady)
    }

    @Test
    fun `recent average uses the last five attempts`() {
        assertEquals(30.0, summary(0.5, 30, 30, 30, 30, 30, 10, 10).recentAverage!!, 1e-9)
        assertEquals(31, summary(0.5, 26, 31, 28).best!!.score)
    }
}
