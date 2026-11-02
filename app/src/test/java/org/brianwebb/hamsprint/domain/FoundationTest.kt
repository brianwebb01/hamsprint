package org.brianwebb.hamsprint.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FoundationTest {
    private val clock = FakeClock()

    private fun foundation(id: String, vararg questions: String) = Foundation(
        id = id,
        title = "Title $id",
        summary = "Summary",
        points = listOf(
            FoundationPoint("first", "a", listOf(questions[0])),
            FoundationPoint("second", "b", listOf(questions[1])),
        ),
        hook = null,
        questionIds = questions.toList(),
        aid = null,
    )

    /** Plays until the session ends or [limit] cards; acknowledges lessons and previews, answers correctly. */
    private fun TrainingSession.playAll(limit: Int, onCard: (Card, Foundation?) -> Unit = { _, _ -> }) {
        repeat(limit) {
            val card = current ?: return
            val lesson = lesson
            onCard(card, lesson)
            if (lesson != null) acknowledgeLesson()
            if (card.kind == CardKind.PREVIEW) acknowledgePreview() else { answer(card.correctPosition); advance() }
        }
    }

    @Test
    fun `curriculum orders each foundation's questions together, then the rest in their given order`() {
        val pool = fixturePool(6)
        val q = pool.questions.associateBy { it.id }
        val curriculum = Curriculum(listOf(foundation("t-b", "T1A05", "T1A02", "T1A04"), foundation("t-a", "T1A06", "T1A01", "T1A03")))
        val shuffled = listOf("T1A03", "T1A01", "T1A06", "T1A05", "T1A02", "T1A04").map(q::getValue)
        assertEquals(
            listOf("T1A05", "T1A02", "T1A04", "T1A06", "T1A01", "T1A03"),
            curriculum.order(shuffled).map { it.id },
        )
        val partial = Curriculum(listOf(foundation("t-a", "T1A06", "T1A01", "T1A03")))
        assertEquals(listOf("T1A06", "T1A01", "T1A03", "T1A05", "T1A02", "T1A04"), partial.order(shuffled).map { it.id })
        assertEquals(2, curriculum.numberOf(curriculum.foundationOf("T1A01")!!))
        assertNull(partial.foundationOf("T1A02"))
    }

    @Test
    fun `an empty curriculum keeps the original order`() {
        val questions = fixturePool(4).questions.reversed()
        assertEquals(questions, Curriculum.EMPTY.order(questions))
    }

    @Test
    fun `a question belongs to one foundation and points only answer listed questions`() {
        assertThrows(IllegalArgumentException::class.java) {
            Curriculum(listOf(foundation("t-a", "T1A01", "T1A02", "T1A03"), foundation("t-b", "T1A03", "T1A04", "T1A05")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Foundation("t-x", "t", "s", listOf(FoundationPoint("l", "v", listOf("T9Z99")), FoundationPoint("l", "v", listOf("T1A01"))), null, listOf("T1A01", "T1A02", "T1A03"), null)
        }
        val f = foundation("t-a", "T1A01", "T1A02", "T1A03")
        assertEquals(listOf("first"), f.pointsFor("T1A01").map { it.label })
        assertTrue(f.pointsFor("T1A03").isEmpty())
    }

    @Test
    fun `training teaches a foundation once, right before its first question, then its questions follow`() {
        val pool = fixturePool(1, 8, 8)
        val curriculum = Curriculum(listOf(foundation("t-f", "T3A07", "T2A03", "T3A01")))
        val s = TrainingSession(pool, pool.questions, emptyMap(), clock, Random(42), curriculum = curriculum)
        val lessons = mutableListOf<Pair<String, String>>()
        val introduced = mutableListOf<String>()
        s.playAll(40) { card, lesson ->
            lesson?.let { lessons += it.id to card.question.id }
            if (card.kind == CardKind.PREVIEW) introduced += card.question.id
        }
        assertEquals("taught once, before its first question", listOf("t-f" to "T3A07"), lessons)
        assertEquals("its questions come first, in curriculum order", listOf("T3A07", "T2A03", "T3A01"), introduced.take(3))
    }

    @Test
    fun `a foundation already seen is not taught again, and reviews never carry a lesson`() {
        val pool = fixturePool(4)
        val curriculum = Curriculum(listOf(foundation("t-f", "T1A01", "T1A02", "T1A03")))
        val s = TrainingSession(pool, pool.questions, emptyMap(), clock, Random(1), curriculum = curriculum, seenFoundations = setOf("t-f"))
        s.playAll(20) { _, lesson -> assertNull(lesson) }

        val reviewing = TrainingSession(
            pool, pool.questions, pool.questions.associate { it.id to progressAt(it.id, 2, dueAt = clock.now) },
            clock, Random(1), curriculum = curriculum,
        )
        assertEquals(CardKind.REVIEW, reviewing.current!!.kind)
        assertNull(reviewing.lesson)
        assertThrows(IllegalStateException::class.java) { reviewing.acknowledgeLesson() }
    }

    @Test
    fun `without previews the lesson still comes before the first guess`() {
        val pool = fixturePool(4)
        val curriculum = Curriculum(listOf(foundation("t-f", "T1A02", "T1A01", "T1A03")))
        val s = TrainingSession(pool, pool.questions, emptyMap(), clock, Random(1), TrainingConfig(previewNew = false), curriculum)
        assertEquals(CardKind.NEW, s.current!!.kind)
        assertEquals("T1A02", s.current!!.question.id)
        assertNotNull(s.lesson)
        assertEquals("t-f", s.acknowledgeLesson())
        assertNull(s.lesson)
    }
}
