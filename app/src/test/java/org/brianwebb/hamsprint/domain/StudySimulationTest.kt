package org.brianwebb.hamsprint.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * End-to-end check of the product promise: a simulated learner studies the
 * General pool through the real training engine, and we measure study time
 * until the pass estimate reaches "ready", then grade them the next day on
 * fresh practice exams using their *true* memory, not the app's estimate.
 *
 * Memory follows FSRS v4 (the model behind Anki's scheduler, fit on hundreds of
 * millions of real reviews) with its published default weights, pessimistically
 * set to high difficulty (unfamiliar technical material). Retrievability decays
 * as a power law; successful retrievals after a gap grow stability far more than
 * massed ones; a forgotten answer is still guessed right 25% of the time.
 */
class StudySimulationTest {
    private class Learner(private val random: Random) {
        private val stability = HashMap<String, Double>() // days until recall falls to 90%
        private val lastSeen = HashMap<String, Long>()
        private val difficulty = 8.0 // FSRS scale 1..10; 8 = hard

        fun recallProbability(id: String, now: Long): Double {
            val s = stability[id] ?: return 0.0
            val days = (now - lastSeen.getValue(id)) / 86_400_000.0
            return 1.0 / (1.0 + days / (9.0 * s))
        }

        fun answers(id: String, now: Long): Boolean =
            random.nextDouble() < recallProbability(id, now) || random.nextDouble() < 0.25

        fun expose(id: String, now: Long, correct: Boolean) {
            val s = stability[id]
            stability[id] = when {
                // First sight (the preview): a weak trace, well below FSRS's "Again" (0.4 d).
                s == null -> 0.05
                correct -> {
                    val r = recallProbability(id, now)
                    s * (1 + exp(1.6) * (11 - difficulty) * s.pow(-0.15) * (exp(1.0 * (1 - r)) - 1))
                }
                else -> {
                    val r = recallProbability(id, now)
                    minOf(s, 2.0 * difficulty.pow(-0.1) * ((s + 1).pow(0.3) - 1) * exp(2.0 * (1 - r))).coerceAtLeast(0.01)
                }
            }
            lastSeen[id] = now
        }
    }

    private data class Outcome(val studied: Duration, val nextDayPassRate: Double, val meanScore: Double)

    private fun simulate(license: LicenseClass, seed: Int): Outcome {
        val pool = realPools.load(license)
        val random = Random(seed)
        val clock = FakeClock()
        val learner = Learner(random)
        var progress = emptyMap<String, QuestionProgress>()
        var studyMillis = 0L
        var sittingElapsed = 0L

        fun spend(millis: Long) {
            clock.now += millis; studyMillis += millis; sittingElapsed += millis
        }

        while (Readiness.evaluate(pool, progress, clock.now).passProbability < Readiness.READY_THRESHOLD) {
            check(studyMillis < 12.hours.inWholeMilliseconds) { "$license seed $seed never became ready" }
            val session = TrainingSession(pool, pool.questions, progress, clock, random)
            while (!session.isFinished) {
                val card = session.current!!
                val id = card.question.id
                if (card.kind == CardKind.PREVIEW) {
                    learner.expose(id, clock.now, correct = true)
                    session.acknowledgePreview()
                    spend(PREVIEW_MILLIS)
                } else {
                    val correct = learner.answers(id, clock.now)
                    session.answer(if (correct) card.correctPosition else (card.correctPosition + 1) % 4)
                    learner.expose(id, clock.now, correct)
                    session.advance()
                    spend(ANSWER_MILLIS)
                }
            }
            progress = session.progress
            // Study in ~50-minute sittings with a 2-hour break between them.
            if (sittingElapsed >= SITTING.inWholeMilliseconds) {
                clock.now += 2.hours.inWholeMilliseconds
                sittingElapsed = 0
            }
        }

        // Exam day: the next morning, graded on the learner's true memory.
        clock.now += 12.hours.inWholeMilliseconds
        val scores = (1..400).map {
            Exam.generate(pool, random, shuffleAnswers = true).cards.count { learner.answers(it.question.id, clock.now) }
        }
        return Outcome(
            studied = studyMillis.milliseconds,
            nextDayPassRate = scores.count { it >= pool.passingScore } / scores.size.toDouble(),
            meanScore = scores.average(),
        )
    }

    @Test
    fun `a typical learner is ready for General in about four hours and passes the next day`() {
        val outcomes = (1..5).map { seed -> simulate(LicenseClass.GENERAL, seed) }
        outcomes.forEach { println("General: $it") }
        assertTrue("every run ready within 4.5h: $outcomes", outcomes.all { it.studied < 4.5.hours })
        val passRate = outcomes.map { it.nextDayPassRate }.average()
        assertTrue("mean next-day pass rate $passRate should honor the 95% readiness bar", passRate >= 0.95)
    }

    @Test
    fun `readiness is calibrated for Technician and Extra too`() {
        for (license in listOf(LicenseClass.TECHNICIAN, LicenseClass.EXTRA)) {
            val outcomes = (1..2).map { simulate(license, it) }
            outcomes.forEach { println("$license: $it") }
            assertTrue("$license: $outcomes", outcomes.map { it.nextDayPassRate }.average() >= 0.93)
        }
    }

    private companion object {
        // Measured-style pacing: reading a preview vs. answering a question.
        const val PREVIEW_MILLIS = 9_000L
        const val ANSWER_MILLIS = 8_000L
        val SITTING = 50.minutes
    }
}
