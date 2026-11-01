package org.brianwebb.hamsprint.data

import org.brianwebb.hamsprint.domain.Band
import org.brianwebb.hamsprint.domain.BandPlanAid
import org.brianwebb.hamsprint.domain.DecibelAid
import org.brianwebb.hamsprint.domain.FactAid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AidLoaderTest {
    private val loader = AidLoader { error("not used") }

    @Test
    fun `parses entries into domain aids keyed by question`() {
        val aids = loader.parse(
            """[
              {"q": "G1A05", "kind": "bandplan", "band": "40m", "mark": [7.125, 7.175], "caption": "Hook"},
              {"q": "T1F02", "kind": "fact", "value": "10", "unit": "minutes", "label": "Identify every"},
              {"q": "T5B09", "kind": "decibel", "ratio": 2}
            ]""",
        )
        assertEquals(BandPlanAid(Band.B40M, 7.125..7.175, "Hook"), aids["G1A05"])
        assertEquals(FactAid("10", "minutes", "Identify every", null), aids["T1F02"])
        assertEquals(2.0, (aids["T5B09"] as DecibelAid).ratio, 1e-9)
    }

    @Test
    fun `an empty file means no aids`() {
        assertTrue(loader.parse("[]").isEmpty())
    }

    @Test
    fun `malformed entries fail loudly and name the question`() {
        fun failure(json: String) = assertThrows(IllegalArgumentException::class.java) { loader.parse(json) }.message.orEmpty()
        assertTrue(failure("""[{"q": "T1A01", "kind": "hologram"}]""").contains("T1A01"))
        assertTrue(failure("""[{"q": "T1A02", "kind": "bandplan", "band": "11m"}]""").contains("unknown band"))
        assertTrue(failure("""[{"q": "T1A03", "kind": "bandplan", "band": "20m", "mark": 7.1}]""").contains("T1A03"))
        assertTrue(failure("""[{"q": "T5D01", "kind": "ohms", "solve": "R", "E": 12}]""").contains("T5D01"))
        assertTrue(failure("""[{"q": "T1A04", "kind": "fact", "label": "x"}]""").contains("missing value"))
        assertTrue(
            failure("""[{"q": "T1A05", "kind": "codes", "set": "q", "highlight": "QRM"}, {"q": "T1A05", "kind": "logic", "gate": "OR"}]""")
                .contains("More than one aid"),
        )
    }
}
