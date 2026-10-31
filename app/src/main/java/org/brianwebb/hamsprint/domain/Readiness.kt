package org.brianwebb.hamsprint.domain

import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class SubelementReadiness(
    val subelement: Subelement,
    /** Mean exam-day recall estimate across the subelement's questions, 0..1. */
    val recall: Double,
    val mastered: Int,
    val total: Int,
)

data class ReadinessReport(
    val passProbability: Double,
    val expectedScore: Double,
    val totalQuestions: Int,
    val unseen: Int,
    val learning: Int,
    val mastered: Int,
    val due: Int,
    val subelements: List<SubelementReadiness>,
    /** Rough study time still needed to reach [Readiness.READY_THRESHOLD]. */
    val timeToReady: Duration,
) {
    val isReady: Boolean get() = passProbability >= Readiness.READY_THRESHOLD
    val seen: Int get() = totalQuestions - unseen
}

/**
 * Turns per-question progress into the probability of passing the real exam.
 *
 * A VE-administered exam draws one question uniformly from each group, so the
 * exam score is a sum of independent Bernoulli trials, one per group, each with
 * success probability equal to the group's mean recall. The pass probability is
 * the upper tail of that Poisson-binomial distribution at the passing score.
 */
object Readiness {
    const val READY_THRESHOLD = 0.95

    /** Per-question study cost, calibrated against StudySimulationTest (preview + spaced recalls). */
    private val NEW_QUESTION_COST = 34.seconds
    private val LEARNING_QUESTION_COST = 12.seconds

    fun evaluate(
        pool: QuestionPool,
        progress: Map<String, QuestionProgress>,
        nowMillis: Long,
    ): ReadinessReport {
        val recall = pool.questions.associate { it.id to MemoryModel.recallProbability(progress[it.id], nowMillis) }
        val groupRecall = pool.groups.map { g -> g.questions.sumOf { recall.getValue(it.id) } / g.questions.size }

        var unseen = 0
        var learning = 0
        var mastered = 0
        var due = 0
        for (q in pool.questions) {
            val p = progress[q.id]
            when {
                p == null -> unseen++
                p.stage >= MemoryModel.MASTERED_STAGE -> mastered++
                else -> learning++
            }
            if (p != null && MemoryModel.isDue(p, nowMillis)) due++
        }

        val subelements = pool.subelements.map { sub ->
            val qs = sub.questions
            SubelementReadiness(
                subelement = sub,
                recall = qs.sumOf { recall.getValue(it.id) } / qs.size,
                mastered = qs.count { (progress[it.id]?.stage ?: -1) >= MemoryModel.MASTERED_STAGE },
                total = qs.size,
            )
        }

        return ReadinessReport(
            passProbability = passProbability(groupRecall, pool.passingScore),
            expectedScore = groupRecall.sum(),
            totalQuestions = pool.questions.size,
            unseen = unseen,
            learning = learning,
            mastered = mastered,
            due = due,
            subelements = subelements,
            timeToReady = timeToReady(pool, progress, nowMillis),
        )
    }

    /** P(at least [passingScore] successes) for independent trials with the given probabilities. */
    fun passProbability(probabilities: List<Double>, passingScore: Int): Double {
        // dist[k] = P(exactly k successes so far)
        var dist = DoubleArray(probabilities.size + 1).also { it[0] = 1.0 }
        for ((i, p) in probabilities.withIndex()) {
            val next = DoubleArray(dist.size)
            for (k in 0..i) {
                next[k] += dist[k] * (1 - p)
                next[k + 1] += dist[k] * p
            }
            dist = next
        }
        return dist.drop(passingScore).sum().coerceIn(0.0, 1.0)
    }

    /**
     * Greedy estimate: learn questions in the order training introduces them
     * (highest exam weight first) until the pass probability crosses the
     * threshold, assuming each learned question reaches a short-term-review level.
     */
    private fun timeToReady(
        pool: QuestionPool,
        progress: Map<String, QuestionProgress>,
        nowMillis: Long,
    ): Duration {
        val learnedRecall = MemoryModel.recallProbability(
            QuestionProgress("", 2, nowMillis, 0, 0, 0, nowMillis), nowMillis,
        )
        val recall = pool.questions.associate { it.id to MemoryModel.recallProbability(progress[it.id], nowMillis) }
            .toMutableMap()
        val groupSizes = pool.groups.associate { it.id to it.questions.size }

        fun currentPass(): Double = passProbability(
            pool.groups.map { g -> g.questions.sumOf { recall.getValue(it.id) } / g.questions.size },
            pool.passingScore,
        )

        var cost = Duration.ZERO
        if (currentPass() >= READY_THRESHOLD) return cost

        val candidates = pool.questions
            .filter { recall.getValue(it.id) < learnedRecall }
            .sortedByDescending { 1.0 / groupSizes.getValue(it.groupId) }
        // Re-evaluating the tail after every question is O(n² · groups); batching keeps it cheap.
        val step = ceil(candidates.size / 60.0).toInt().coerceAtLeast(1)
        for (batch in candidates.chunked(step)) {
            for (q in batch) {
                cost += if (progress[q.id] == null) NEW_QUESTION_COST else LEARNING_QUESTION_COST
                recall[q.id] = learnedRecall
            }
            if (currentPass() >= READY_THRESHOLD) break
        }
        return cost
    }
}
