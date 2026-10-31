package org.brianwebb.hamsprint.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TrainingSessionTest {
    private val clock = FakeClock()

    private fun session(
        pool: QuestionPool,
        progress: Map<String, QuestionProgress> = emptyMap(),
        config: TrainingConfig = TrainingConfig(),
        scope: List<Question> = pool.questions,
    ) = TrainingSession(pool, scope, progress, clock, Random(42), config)

    /** Answers the current card correctly (or not) and advances; acknowledges previews. */
    private fun TrainingSession.play(correct: Boolean = true): Card {
        val card = current!!
        if (card.kind == CardKind.PREVIEW) {
            acknowledgePreview()
        } else {
            val pos = if (correct) card.correctPosition else (card.correctPosition + 1) % 4
            answer(pos)
            advance()
        }
        return card
    }

    @Test
    fun `a new question is previewed first, then recalled once a few cards later to graduate`() {
        val pool = fixturePool(1, 10, 10)
        val s = session(pool)
        val first = s.current!!
        assertEquals(CardKind.PREVIEW, first.kind)
        val id = first.question.id

        s.play()
        assertEquals(MemoryModel.LEARNING, s.progress.getValue(id).stage)

        val recalls = mutableListOf<Int>()
        repeat(15) {
            val card = s.play()
            if (card.question.id == id) recalls += s.cardsShown
        }
        assertEquals("graduates after exactly one recall", 1, recalls.size)
        assertTrue("recall is spaced from the preview", recalls[0] - 1 >= 3)
        val graduated = s.progress.getValue(id)
        assertEquals(1, graduated.stage)
        assertEquals(clock.now + MemoryModel.interval(1).inWholeMilliseconds, graduated.dueAtMillis)
    }

    @Test
    fun `new questions from small groups come first because they are likelier on the exam`() {
        val pool = fixturePool(12, 2, 12)
        val s = session(pool)
        assertEquals("T2A", s.current!!.question.groupId)
    }

    @Test
    fun `a missed question lapses and comes back within a few cards`() {
        val pool = fixturePool(10, 10, 10)
        val id = pool.questions.first().id
        val s = session(pool, mapOf(id to progressAt(id, 4, dueAt = clock.now - 1)))
        val card = s.current!!
        assertEquals(CardKind.REVIEW, card.kind)
        assertEquals(id, card.question.id)

        val result = s.answer((card.correctPosition + 1) % 4)
        assertFalse(result.correct)
        assertEquals(MemoryModel.LEARNING, result.progress.stage)
        assertEquals(1, result.progress.lapses)
        s.advance()

        val seenAgainAt = (1..5).firstOrNull { s.play().question.id == id }
        assertTrue("missed question returns within 5 cards, was $seenAgainAt", seenAgainAt != null && seenAgainAt >= 2)
    }

    @Test
    fun `a review backlog does not stop new questions from being introduced`() {
        val pool = fixturePool(30, 30)
        val reviewed = pool.questions.take(20)
        val progress = reviewed.associate { it.id to progressAt(it.id, 3, dueAt = clock.now - 1000) }
        val s = session(pool, progress, TrainingConfig(roundLength = 12))
        val kinds = generateSequence { s.current?.also { s.play() }?.kind }.toList()
        assertTrue("new questions interleave with reviews: $kinds", kinds.count { it == CardKind.PREVIEW } >= 2)
        assertTrue(kinds.count { it == CardKind.REVIEW } >= 6)
    }

    @Test
    fun `due reviews are served before new questions`() {
        val pool = fixturePool(5, 5)
        val due = pool.questions.last().id
        val s = session(pool, mapOf(due to progressAt(due, 3, dueAt = clock.now - 1000)))
        assertEquals(due, s.current!!.question.id)
        assertEquals(CardKind.REVIEW, s.current!!.kind)
    }

    @Test
    fun `a correct review promotes one stage on the spaced schedule`() {
        val pool = fixturePool(3)
        val id = pool.questions.first().id
        val s = session(pool, mapOf(id to progressAt(id, 2, dueAt = clock.now)))
        val r = s.answer(s.current!!.correctPosition)
        assertTrue(r.correct)
        assertEquals(3, r.progress.stage)
        assertEquals(clock.now + MemoryModel.interval(3).inWholeMilliseconds, r.progress.dueAtMillis)
    }

    @Test
    fun `working memory never holds more than the configured number of new questions`() {
        val pool = fixturePool(30)
        val s = session(pool, config = TrainingConfig(workingSetSize = 3, roundLength = 60))
        var maxInFlight = 0
        while (!s.isFinished) {
            s.play(correct = false)
            maxInFlight = maxOf(maxInFlight, s.progress.values.count { it.stage == MemoryModel.LEARNING })
        }
        assertEquals(3, maxInFlight)
    }

    @Test
    fun `a round ends after roundLength cards`() {
        val pool = fixturePool(40)
        val s = session(pool, config = TrainingConfig(roundLength = 7))
        var shown = 0
        while (!s.isFinished) { s.play(); shown++ }
        assertEquals(7, shown)
        assertFalse(s.summary().caughtUp)
    }

    @Test
    fun `with nothing due, new, or weak the round reports caught up`() {
        val pool = fixturePool(2)
        val progress = pool.questions.associate { it.id to progressAt(it.id, 5, dueAt = clock.now + 86_400_000) }
        val s = session(pool, progress, TrainingConfig(reviewAhead = false))
        assertNull(s.current)
        assertTrue(s.summary().caughtUp)
    }

    @Test
    fun `review-ahead keeps a fully learned learner practicing their weakest question`() {
        val pool = fixturePool(3)
        val ids = pool.questions.map { it.id }
        val progress = mapOf(
            ids[0] to progressAt(ids[0], 6, clock.now + 1_000_000),
            ids[1] to progressAt(ids[1], 2, clock.now + 1_000_000),
            ids[2] to progressAt(ids[2], 6, clock.now + 1_000_000),
        )
        val s = session(pool, progress)
        assertEquals(CardKind.AHEAD, s.current!!.kind)
        assertEquals(ids[1], s.current!!.question.id)
        val r = s.answer(s.current!!.correctPosition)
        assertEquals("early practice does not promote", 2, r.progress.stage)
    }

    @Test
    fun `shuffled answers still grade by content`() {
        val pool = fixturePool(10, 10)
        val s = session(pool, config = TrainingConfig(previewNew = false, shuffleAnswers = true))
        val orders = mutableSetOf<List<Int>>()
        repeat(10) {
            val card = s.current!!
            orders += card.answerOrder
            assertEquals("right", card.answerAt(card.correctPosition))
            assertTrue(s.answer(card.correctPosition).correct)
            s.advance()
        }
        assertTrue("answer order varies", orders.size > 1)
    }

    @Test(expected = IllegalStateException::class)
    fun `answering the same card twice is rejected`() {
        val pool = fixturePool(3)
        val s = session(pool, config = TrainingConfig(previewNew = false))
        val pos = s.current!!.correctPosition
        s.answer(pos)
        s.answer(pos)
    }

    @Test
    fun `topic scope restricts every card to that topic`() {
        val pool = fixturePool(5, 5, 5)
        val topic = pool.subelements[1]
        val s = session(pool, scope = topic.questions, config = TrainingConfig(roundLength = 30))
        while (!s.isFinished) {
            assertEquals(topic.groups.single().id, s.current!!.question.groupId)
            s.play()
        }
    }

    @Test
    fun `the same question is never shown twice in a row when alternatives exist`() {
        val pool = fixturePool(4, 4)
        val s = session(pool, config = TrainingConfig(roundLength = 80))
        var last: String? = null
        var i = 0
        while (!s.isFinished) {
            val id = s.current!!.question.id
            assertNotEquals(last, id)
            last = id
            s.play(correct = i++ % 3 != 0)
        }
    }
}
