package org.brianwebb.hamsprint.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * What the learner knows about one question. A question with no progress has
 * never been seen.
 *
 * [stage] 0 means "learning": it was missed (or introduced and not yet locked in)
 * and should come back as soon as possible. Stages 1+ are spaced reviews whose
 * interval grows with each successful recall.
 */
data class QuestionProgress(
    val questionId: String,
    val stage: Int,
    val dueAtMillis: Long,
    val timesSeen: Int,
    val timesCorrect: Int,
    val lapses: Int,
    val lastAnsweredAtMillis: Long,
)

/**
 * Spaced-retrieval schedule tuned for a short, intense prep window: the early
 * steps are minutes and hours apart so that a learner cramming over a few
 * sittings still gets several spaced recalls of every question before exam day.
 */
object MemoryModel {
    const val LEARNING = 0
    const val MAX_STAGE = 7

    /** A question at this stage or above counts as "mastered" in the UI. */
    const val MASTERED_STAGE = 3

    private val intervals: List<Duration> = listOf(
        Duration.ZERO, // learning: due immediately
        10.minutes,
        1.hours,
        6.hours,
        1.days,
        3.days,
        8.days,
        21.days,
    )

    /**
     * Estimated probability of picking the right answer on the exam, by stage.
     * The exam is multiple choice (recognition, not free recall), so a question
     * recalled correctly after a spaced gap is likely to be answered correctly
     * again; unseen questions are a 1-in-4 guess. Stage 1 (learned, but never
     * recalled after a gap) is deliberately pessimistic so the pass estimate
     * only climbs once spaced recalls actually happen.
     *
     * Calibrated with StudySimulationTest: when the estimate first crosses the
     * 95% "ready" bar, simulated learners pass ~97% of next-day practice exams.
     */
    private val recall = doubleArrayOf(0.50, 0.70, 0.80, 0.88, 0.93, 0.96, 0.97, 0.98)
    const val GUESS = 0.25

    fun interval(stage: Int): Duration = intervals[stage.coerceIn(0, MAX_STAGE)]

    /** Exam-day recall estimate for a question; overdue reviews decay one stage. */
    fun recallProbability(progress: QuestionProgress?, nowMillis: Long): Double {
        if (progress == null) return GUESS
        val stage = progress.stage
        if (stage <= LEARNING) return recall[LEARNING]
        val overdueBy = nowMillis - progress.dueAtMillis
        val decayed = overdueBy > interval(stage).inWholeMilliseconds
        return recall[if (decayed) stage - 1 else stage]
    }

    fun isDue(progress: QuestionProgress, nowMillis: Long): Boolean = progress.dueAtMillis <= nowMillis

    /** A spaced review answered correctly: advance one stage. */
    fun promote(progress: QuestionProgress?, questionId: String, nowMillis: Long): QuestionProgress {
        val stage = ((progress?.stage ?: LEARNING) + 1).coerceAtMost(MAX_STAGE)
        return answered(progress, questionId, nowMillis, correct = true).copy(
            stage = stage,
            dueAtMillis = nowMillis + interval(stage).inWholeMilliseconds,
        )
    }

    /** Answered correctly but too early to count as a spaced recall: keep the stage. */
    fun confirm(progress: QuestionProgress?, questionId: String, nowMillis: Long): QuestionProgress =
        answered(progress, questionId, nowMillis, correct = true).let {
            if (progress == null) it.copy(stage = LEARNING, dueAtMillis = nowMillis) else it
        }

    /** Missed: back to learning, due right away. */
    fun lapse(progress: QuestionProgress?, questionId: String, nowMillis: Long): QuestionProgress =
        answered(progress, questionId, nowMillis, correct = false).copy(
            stage = LEARNING,
            dueAtMillis = nowMillis,
            lapses = (progress?.lapses ?: 0) + if (progress != null) 1 else 0,
        )

    private fun answered(
        progress: QuestionProgress?,
        questionId: String,
        nowMillis: Long,
        correct: Boolean,
    ): QuestionProgress {
        val base = progress ?: QuestionProgress(questionId, LEARNING, nowMillis, 0, 0, 0, nowMillis)
        return base.copy(
            timesSeen = base.timesSeen + 1,
            timesCorrect = base.timesCorrect + if (correct) 1 else 0,
            lastAnsweredAtMillis = nowMillis,
        )
    }
}
