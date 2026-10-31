package org.brianwebb.hamsprint.domain

import kotlin.random.Random

/**
 * A practice exam built the way VEs build real ones: one question drawn at
 * random from every group, presented in syllabus order.
 */
data class Exam(
    val pool: QuestionPool,
    val cards: List<Card>,
) {
    val passingScore: Int get() = pool.passingScore

    companion object {
        fun generate(pool: QuestionPool, random: Random, shuffleAnswers: Boolean): Exam =
            Exam(
                pool = pool,
                cards = pool.groups.map { group ->
                    val q = group.questions.random(random)
                    val order = if (shuffleAnswers) q.answers.indices.shuffled(random) else q.answers.indices.toList()
                    Card(q, CardKind.REVIEW, order)
                },
            )
    }

    /** [answers] maps card index to the chosen display position. */
    fun grade(answers: Map<Int, Int>): ExamResult {
        val outcomes = cards.mapIndexed { i, card ->
            val chosen = answers[i]
            ExamOutcome(card, chosen, chosen == card.correctPosition)
        }
        val score = outcomes.count { it.correct }
        val bySub = pool.subelements.map { sub ->
            val subOutcomes = outcomes.filter { o -> sub.groups.any { it.id == o.card.question.groupId } }
            SubelementScore(sub, subOutcomes.count { it.correct }, subOutcomes.size)
        }
        return ExamResult(score, cards.size, passingScore, outcomes, bySub)
    }
}

data class ExamOutcome(val card: Card, val chosenPosition: Int?, val correct: Boolean)

data class SubelementScore(val subelement: Subelement, val correct: Int, val total: Int)

data class ExamResult(
    val score: Int,
    val total: Int,
    val passingScore: Int,
    val outcomes: List<ExamOutcome>,
    val bySubelement: List<SubelementScore>,
) {
    val passed: Boolean get() = score >= passingScore
    val missed: List<ExamOutcome> get() = outcomes.filterNot { it.correct }

    /**
     * Feeds the exam back into the memory model: every miss returns its question
     * to learning so training resurfaces it first. Correct answers are not
     * promoted: a lucky guess on an exam is not a spaced recall.
     */
    fun applyTo(progress: Map<String, QuestionProgress>, nowMillis: Long): List<QuestionProgress> =
        outcomes.map { o ->
            val id = o.card.question.id
            if (o.correct) MemoryModel.confirm(progress[id], id, nowMillis)
            else MemoryModel.lapse(progress[id], id, nowMillis)
        }
}
