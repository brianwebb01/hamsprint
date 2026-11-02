package org.brianwebb.hamsprint.data

import org.brianwebb.hamsprint.domain.Foundation
import org.brianwebb.hamsprint.domain.LicenseClass
import org.brianwebb.hamsprint.domain.Question
import org.brianwebb.hamsprint.domain.realFoundations
import org.brianwebb.hamsprint.domain.realPools
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Guards the bundled foundations (assets/foundations): they only reorganize
 * what the pool's correct answers say, so a framework can never teach a
 * number the exam doesn't.
 */
class FoundationAssetsTest {
    @Test
    fun `every foundation parses and links real questions`() {
        for (license in LicenseClass.entries) {
            val pool = realPools.load(license)
            val curriculum = realFoundations.load(license)
            val linked = curriculum.foundations.flatMap { it.questionIds }
            assertTrue("${license.poolId} has foundations", curriculum.foundations.size >= 10)
            assertTrue("${license.poolId} links at least half its questions", linked.size >= pool.questions.size / 2)
            val unknown = linked.filter { pool.question(it) == null }
            assertTrue("unknown questions $unknown", unknown.isEmpty())
        }
    }

    @Test
    fun `every number in a row comes from the pool`() {
        val wrong = mutableListOf<String>()
        forEachFoundation { f, question ->
            for (p in f.points) {
                val sources = p.questionIds.map(question)
                numbers(p.value).filterNot { n -> sources.any { contains(it.correctAnswer, n) } }
                    .forEach { wrong += "${f.id} \"${p.value}\": $it is not in a correct answer of ${p.questionIds}" }
                numbers(p.label).filterNot { n -> n in bandNumbers(p.label) || sources.any { contains(fullText(it), n) } }
                    .forEach { wrong += "${f.id} \"${p.label}\": $it is not in the text of ${p.questionIds}" }
            }
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun `every number in a summary or hook is backed by a row or a linked question`() {
        val wrong = mutableListOf<String>()
        forEachFoundation { f, question ->
            val rows = f.points.joinToString(" ") { "${it.label} ${it.value}" }
            val linked = f.questionIds.joinToString(" ") { fullText(question(it)) }
            for (text in listOfNotNull(f.summary, f.hook)) {
                numbers(text).filterNot { n ->
                    (n == n.toLong().toDouble() && n in 1.0..12.0) || n in bandNumbers(text) || contains(rows, n) || contains(linked, n)
                }.forEach { wrong += "${f.id}: $it in \"$text\"" }
            }
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun `text is short and free of em and en dashes`() {
        val wrong = mutableListOf<String>()
        forEachFoundation { f, _ ->
            val all = listOfNotNull(f.title, f.summary, f.hook) + f.points.flatMap { listOf(it.label, it.value) }
            if (all.any { s -> s.any { it == '–' || it == '—' } }) wrong += "${f.id} has a dash"
            if (f.title.length > 40 || f.summary.length > 140 || (f.hook?.length ?: 0) > 160) wrong += "${f.id} text too long"
            if (f.points.any { it.label.length > 28 || it.value.length > 48 }) wrong += "${f.id} row too long"
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    private fun forEachFoundation(check: (Foundation, (String) -> Question) -> Unit) {
        for (license in LicenseClass.entries) {
            val pool = realPools.load(license)
            realFoundations.load(license).foundations.forEach { f -> check(f) { id -> pool.question(id)!! } }
        }
    }

    private fun fullText(q: Question) = (listOf(q.text) + q.answers).joinToString(" ")

    private fun numbers(text: String): List<Double> = NUMBER.findAll(text).map { it.value.replace(",", "").toDouble() }.toList()

    private fun contains(text: String, n: Double) = numbers(text).any { abs(it - n) < 1e-9 }

    /** Numbers that name a real amateur band, like "30 meters" or "70 cm". */
    private fun bandNumbers(text: String): List<Double> = BAND_NAME.findAll(text).mapNotNull { m ->
        val unit = if (m.groupValues[2].startsWith("c")) "cm" else "m"
        m.groupValues[1].takeIf { org.brianwebb.hamsprint.domain.Band.fromId(it + unit) != null }?.toDouble()
    }.toList()

    private companion object {
        val NUMBER = Regex("""\d[\d,]*(?:\.\d+)?|\.\d+""")
        val BAND_NAME = Regex("""(\d+(?:\.\d+)?)[ -]?(meters?|m|cm|centimeters?)\b""")
    }
}
