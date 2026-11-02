package org.brianwebb.hamsprint.data

import org.brianwebb.hamsprint.domain.Band
import org.brianwebb.hamsprint.domain.SpectrumAid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FoundationLoaderTest {
    private val loader = FoundationLoader { error("not used") }

    private val valid = """{
        "id": "g-power", "title": "Power limits", "summary": "One number, then the exceptions.",
        "points": [
          {"label": "Most bands", "value": "1500 watts PEP output", "q": ["G1C05", "G1C06"]},
          {"label": "30 meters", "value": "200 watts PEP output", "q": ["G1C01"]}
        ],
        "hook": "Big by default, small on 30 meters.",
        "questions": ["G1C01", "G1C05", "G1C06"],
        "aid": {"kind": "spectrum", "bands": ["30m"]}
    }"""

    @Test
    fun `parses foundations in curriculum order with their points and picture`() {
        val curriculum = loader.parse("[$valid]")
        val f = curriculum.foundations.single()
        assertEquals("Power limits", f.title)
        assertEquals(listOf("G1C01", "G1C05", "G1C06"), f.questionIds)
        assertEquals(listOf("G1C05", "G1C06"), f.points[0].questionIds)
        assertEquals("Big by default, small on 30 meters.", f.hook)
        assertEquals(listOf(Band.B30M), (f.aid as SpectrumAid).bands)
        assertEquals(f, curriculum.foundationOf("G1C06"))
    }

    @Test
    fun `an empty file means no foundations, and hook and aid are optional`() {
        assertTrue(loader.parse("[]").foundations.isEmpty())
        val bare = valid.replace(Regex(""""hook": "[^"]*",\s*"""), "").replace(Regex(""",\s*"aid": \{[^}]*\}"""), "")
        val f = loader.parse("[$bare]").foundations.single()
        assertNull(f.hook)
        assertNull(f.aid)
    }

    @Test
    fun `malformed foundations fail loudly and name the foundation`() {
        fun failure(json: String) = assertThrows(IllegalArgumentException::class.java) { loader.parse("[$json]") }.message.orEmpty()
        assertTrue(failure(valid.replace(""""title": "Power limits",""", "")).contains("g-power"))
        assertTrue(failure(valid.replace(""""questions": ["G1C01", "G1C05", "G1C06"]""", """"questions": ["G1C05", "G1C06"]""")).contains("g-power"))
        assertTrue(failure(valid.replace(""""bands": ["30m"]""", """"bands": ["11m"]""")).contains("unknown band"))
        assertTrue(failure("$valid, $valid").contains("unique"))
    }
}
