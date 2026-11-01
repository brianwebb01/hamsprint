package org.brianwebb.hamsprint.domain

import org.brianwebb.hamsprint.data.PoolLoader
import java.io.File
import java.time.LocalDate

/** Settable clock for deterministic scheduling tests. */
class FakeClock(var now: Long = 1_000_000_000L) : Clock {
    override fun nowMillis() = now
    fun advanceMinutes(m: Long) { now += m * 60_000 }
}

/** The real bundled pools, read straight from the app's assets directory. */
val realPools = PoolLoader { path -> File("src/main/assets/$path").inputStream() }

/**
 * A synthetic pool: one subelement per entry of [groupSizes] (one group each),
 * questions whose correct answer is always index 0.
 */
fun fixturePool(vararg groupSizes: Int, passingScore: Int = groupSizes.size): QuestionPool {
    val subs = groupSizes.mapIndexed { i, size ->
        val gid = "T${i + 1}A"
        Subelement(
            id = "T${i + 1}",
            title = "Topic ${i + 1}",
            groups = listOf(
                QuestionGroup(gid, "Group $gid", (1..size).map { n ->
                    Question("$gid%02d".format(n), gid, "Question $gid$n?", listOf("right", "w1", "w2", "w3"), 0, null, null)
                }),
            ),
        )
    }
    return QuestionPool(
        license = LicenseClass.TECHNICIAN,
        name = "Fixture",
        validFrom = LocalDate.of(2026, 7, 1),
        validThrough = LocalDate.of(2030, 6, 30),
        revision = "test",
        examQuestions = groupSizes.size,
        passingScore = passingScore,
        subelements = subs,
    )
}

fun progressAt(id: String, stage: Int, dueAt: Long, now: Long = dueAt) =
    QuestionProgress(id, stage, dueAt, timesSeen = 3, timesCorrect = 3, lapses = 0, lastAnsweredAtMillis = now)

/** The real bundled visual aids. */
val realAids = org.brianwebb.hamsprint.data.AidLoader { path -> File("src/main/assets/$path").inputStream() }
