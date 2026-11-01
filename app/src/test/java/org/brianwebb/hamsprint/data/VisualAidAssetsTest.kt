package org.brianwebb.hamsprint.data

import org.brianwebb.hamsprint.domain.AntennaAid
import org.brianwebb.hamsprint.domain.BandPlanAid
import org.brianwebb.hamsprint.domain.CombineAid
import org.brianwebb.hamsprint.domain.DecibelAid
import org.brianwebb.hamsprint.domain.FactAid
import org.brianwebb.hamsprint.domain.FrequencyUnit
import org.brianwebb.hamsprint.domain.LicenseClass
import org.brianwebb.hamsprint.domain.OhmsAid
import org.brianwebb.hamsprint.domain.PowerAid
import org.brianwebb.hamsprint.domain.PrefixAid
import org.brianwebb.hamsprint.domain.Question
import org.brianwebb.hamsprint.domain.ReactanceAid
import org.brianwebb.hamsprint.domain.SpectrumAid
import org.brianwebb.hamsprint.domain.SwrAid
import org.brianwebb.hamsprint.domain.TimeConstantAid
import org.brianwebb.hamsprint.domain.TransformerAid
import org.brianwebb.hamsprint.domain.VisualAid
import org.brianwebb.hamsprint.domain.WaveformAid
import org.brianwebb.hamsprint.domain.WavelengthAid
import org.brianwebb.hamsprint.domain.realAids
import org.brianwebb.hamsprint.domain.realPools
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Guards the bundled aids (assets/aids): every aid targets a real question and
 * every computed result appears in that question's correct answer, so an aid
 * can never teach a wrong number.
 */
class VisualAidAssetsTest {
    @Test
    fun `every bundled aid parses and targets a question in its pool`() {
        for (license in LicenseClass.entries) {
            val pool = realPools.load(license)
            val aids = realAids.load(license)
            assertTrue("${license.poolId} has aids", aids.size > pool.questions.size / 4)
            val unknown = aids.keys.filter { pool.question(it) == null }
            assertTrue("unknown questions $unknown", unknown.isEmpty())
        }
    }

    @Test
    fun `computed results match the correct answer`() {
        val wrong = mutableListOf<String>()
        for (license in LicenseClass.entries) {
            val pool = realPools.load(license)
            realAids.load(license).forEach { (id, aid) ->
                val expected = computed(aid) ?: return@forEach
                val answer = pool.question(id)!!.correctAnswer
                val found = answerValues(answer)
                if (expected.none { e -> found.any { close(it, e) } }) wrong += "$id: $expected vs \"$answer\""
            }
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun `key numbers are quoted from the correct answer`() {
        for (license in LicenseClass.entries) {
            val pool = realPools.load(license)
            realAids.load(license).forEach { (id, aid) ->
                if (aid is FactAid) assertTrue(id, pool.question(id)!!.correctAnswer.contains(aid.value))
            }
        }
    }

    @Test
    fun `frequencies are written in the unit their question uses`() {
        val wrong = mutableListOf<String>()
        for (license in LicenseClass.entries) {
            val pool = realPools.load(license)
            realAids.load(license).forEach { (id, aid) ->
                val q = pool.question(id)!!
                val expected = questionUnit(q)
                val shown = when (aid) {
                    is BandPlanAid -> aid.unit to FrequencyUnit.conventional(aid.band.lowMHz)
                    is SpectrumAid -> aid.markMHz?.let { aid.markUnit to FrequencyUnit.conventional(it) }
                    else -> null
                }
                if (shown != null && shown.first != (expected ?: shown.second)) wrong += "$id shows ${shown.first.symbol}"
                val caption = aid.caption.orEmpty()
                val inKhz = FREQ.findAll(caption).any { it.groupValues[2] == "kHz" && it.groupValues[1].replace(",", "").toDouble() >= 1000 }
                if (expected == FrequencyUnit.KHZ && "MHz" in caption) wrong += "$id caption uses MHz"
                if (expected != FrequencyUnit.KHZ && inKhz) wrong += "$id caption uses kHz"
            }
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    /**
     * kHz when the question writes a radio frequency of 1 MHz or more in kHz
     * ("21300 kHz"), MHz when it writes one in MHz, else null. The correct
     * answer decides; the stem only when that answer names no frequency.
     */
    private fun questionUnit(q: Question): FrequencyUnit? {
        for (text in listOf(q.correctAnswer, q.text)) {
            val found = FREQ.findAll(text).map { it.groupValues[1].replace(",", "").toDouble() to it.groupValues[2] }.toList()
            if (found.any { (n, u) -> u == "kHz" && n >= 1000 }) return FrequencyUnit.KHZ
            if (found.any { (_, u) -> u == "MHz" }) return FrequencyUnit.MHZ
        }
        return null
    }

    @Test
    fun `captions are short and free of em and en dashes`() {
        for (license in LicenseClass.entries) {
            realAids.load(license).forEach { (id, aid) ->
                val text = listOfNotNull(aid.caption, (aid as? FactAid)?.label, (aid as? FactAid)?.unit).joinToString(" ")
                assertTrue("$id has a dash", text.none { it == '–' || it == '—' })
                assertTrue("$id caption too long", (aid.caption?.length ?: 0) <= 90)
            }
        }
    }

    private fun computed(aid: VisualAid): List<Double>? = when (aid) {
        is OhmsAid -> aid.result?.let(::listOf)
        is PowerAid -> aid.result?.let(::listOf)
        is CombineAid -> listOf(aid.total)
        is DecibelAid -> aid.resultWatts?.let(::listOf)
            ?: listOf(aid.ratio, aid.db, abs(1 - aid.ratio) * 100, aid.ratio * 100, 1 / aid.ratio)
        is PrefixAid -> listOf(aid.result)
        is WaveformAid -> listOf(aid.result)
        is SwrAid -> aid.impedances?.let { listOf(aid.swr) }
        is ReactanceAid -> aid.result?.let(::listOf)
        is TimeConstantAid -> aid.seconds?.let(::listOf)
        is TransformerAid -> aid.secondaryVolts?.let(::listOf)
            ?: listOf(aid.primaryTurns / aid.secondaryTurns, aid.secondaryTurns / aid.primaryTurns)
        is WavelengthAid -> if (aid.example) null else listOf(if (aid.fromMeters) aid.freqMHz else aid.meters)
        is AntennaAid -> aid.lengthFeet?.let { listOf(it, it * 12) }
        else -> null
    }

    private fun close(a: Double, b: Double) = abs(b) > 0 && abs(abs(a) - abs(b)) / abs(b) <= 0.05

    /** Every number in an answer, as printed and scaled by any SI prefix that follows it. */
    private fun answerValues(text: String): List<Double> = NUMBER.findAll(text).flatMap { m ->
        val n = m.groupValues[1].replace(",", "").toDouble()
        val unit = m.groupValues[2]
        val word = WORDS.entries.firstOrNull { unit.lowercase().startsWith(it.key) }?.value
        val symbol = SYMBOLS[unit.firstOrNull()]?.takeIf { unit.length >= 2 && unit.substring(1).lowercase() in UNITS }
        listOfNotNull(n, (word ?: symbol)?.let { n * it }, if (unit.lowercase().startsWith("f")) n * 12 else null)
    }.toList()

    private companion object {
        val FREQ = Regex("""(\d[\d,]*\.?\d*)\s*(kHz|MHz)""")
        val NUMBER = Regex("""(\d[\d,]*\.?\d*|\.\d+)\s*([a-zA-Zµ]*)""")
        val WORDS = mapOf("pico" to 1e-12, "nano" to 1e-9, "micro" to 1e-6, "milli" to 1e-3, "kilo" to 1e3, "mega" to 1e6, "giga" to 1e9)
        val SYMBOLS = mapOf('p' to 1e-12, 'n' to 1e-9, 'u' to 1e-6, 'µ' to 1e-6, 'm' to 1e-3, 'k' to 1e3, 'M' to 1e6, 'G' to 1e9)
        val UNITS = setOf("hz", "w", "v", "a", "h", "f")
    }
}
