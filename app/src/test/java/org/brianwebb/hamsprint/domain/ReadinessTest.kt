package org.brianwebb.hamsprint.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class ReadinessTest {
    private val now = 1_000_000_000L

    @Test
    fun `pass probability is the Poisson-binomial upper tail`() {
        assertEquals(0.75, Readiness.passProbability(listOf(0.5, 0.5), 1), 1e-12)
        assertEquals(0.25, Readiness.passProbability(listOf(0.5, 0.5), 2), 1e-12)
        assertEquals(0.3 * 0.6, Readiness.passProbability(listOf(0.3, 0.6), 2), 1e-12)
        assertEquals(1.0, Readiness.passProbability(listOf(0.1, 0.2), 0), 1e-12)
    }

    @Test
    fun `a fresh learner guessing has essentially no chance of passing`() {
        val pool = realPools.load(LicenseClass.GENERAL)
        val r = Readiness.evaluate(pool, emptyMap(), now)
        assertTrue(r.passProbability < 1e-4)
        assertEquals(pool.questions.size, r.unseen)
        assertEquals(0, r.seen)
        assertEquals(35 * MemoryModel.GUESS, r.expectedScore, 1e-9)
    }

    @Test
    fun `a learner with every question mastered and current is ready`() {
        val pool = realPools.load(LicenseClass.GENERAL)
        val progress = pool.questions.associate { it.id to progressAt(it.id, 4, dueAt = now + 1) }
        val r = Readiness.evaluate(pool, progress, now)
        assertTrue(r.isReady)
        assertEquals(Duration.ZERO, r.timeToReady)
        assertEquals(pool.questions.size, r.mastered)
    }

    @Test
    fun `overdue reviews decay so readiness drops if you stop studying`() {
        val pool = fixturePool(3, 3)
        val q = pool.questions.first().id
        val fresh = MemoryModel.recallProbability(progressAt(q, 4, dueAt = now), now)
        val stale = MemoryModel.recallProbability(progressAt(q, 4, dueAt = now - 30L * 86_400_000), now)
        assertTrue(stale < fresh)
    }

    @Test
    fun `estimated time to ready from scratch is in the four-hour range for General`() {
        val pool = realPools.load(LicenseClass.GENERAL)
        val r = Readiness.evaluate(pool, emptyMap(), now)
        assertTrue("was ${r.timeToReady}", r.timeToReady in 2.hours..5.hours)
    }
}
