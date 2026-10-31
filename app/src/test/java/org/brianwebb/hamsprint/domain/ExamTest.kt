package org.brianwebb.hamsprint.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExamTest {
    @Test
    fun `an exam draws exactly one question from every group, in syllabus order`() {
        for (license in LicenseClass.entries) {
            val pool = realPools.load(license)
            val exam = Exam.generate(pool, Random(7), shuffleAnswers = true)
            assertEquals(pool.examQuestions, exam.cards.size)
            assertEquals(pool.groups.map { it.id }, exam.cards.map { it.question.groupId })
        }
    }

    @Test
    fun `grading counts correct answers, treats blanks as wrong, and applies the pass mark`() {
        val pool = fixturePool(1, 1, 1, 1, passingScore = 3)
        val exam = Exam.generate(pool, Random(1), shuffleAnswers = true)
        val answers = mapOf(0 to exam.cards[0].correctPosition, 1 to exam.cards[1].correctPosition, 2 to (exam.cards[2].correctPosition + 1) % 4)
        val result = exam.grade(answers)
        assertEquals(2, result.score)
        assertFalse(result.passed)
        assertEquals(2, result.missed.size)
        assertEquals(listOf(1, 1, 0, 0), result.bySubelement.map { it.correct })

        val all = exam.cards.indices.associateWith { exam.cards[it].correctPosition }
        assertTrue(exam.grade(all).passed)
    }

    @Test
    fun `missed exam questions return to learning, correct ones are not promoted`() {
        val pool = fixturePool(1, 1)
        val now = 5_000L
        val exam = Exam.generate(pool, Random(1), shuffleAnswers = false)
        val (q0, q1) = exam.cards.map { it.question.id }
        val progress = mapOf(q0 to progressAt(q0, 3, now + 1), q1 to progressAt(q1, 3, now + 1))
        val result = exam.grade(mapOf(0 to exam.cards[0].correctPosition, 1 to (exam.cards[1].correctPosition + 1) % 4))
        val updates = result.applyTo(progress, now).associateBy { it.questionId }
        assertEquals(3, updates.getValue(q0).stage)
        assertEquals(MemoryModel.LEARNING, updates.getValue(q1).stage)
        assertEquals(now, updates.getValue(q1).dueAtMillis)
    }
}
