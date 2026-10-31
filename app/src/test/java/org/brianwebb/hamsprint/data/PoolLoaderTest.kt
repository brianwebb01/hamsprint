package org.brianwebb.hamsprint.data

import org.brianwebb.hamsprint.domain.LicenseClass
import org.brianwebb.hamsprint.domain.realPools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Guards the bundled data against a bad regeneration of tools/pool/build_pools.py output. */
class PoolLoaderTest {
    @Test
    fun `bundled pools match the current NCVEC releases`() {
        val expected = mapOf(
            LicenseClass.TECHNICIAN to Triple(409, 35, 26),
            LicenseClass.GENERAL to Triple(423, 35, 26),
            LicenseClass.EXTRA to Triple(599, 50, 37),
        )
        for ((license, e) in expected) {
            val pool = realPools.load(license)
            assertEquals(e.first, pool.questions.size)
            assertEquals(e.second, pool.examQuestions)
            assertEquals(e.third, pool.passingScore)
        }
    }

    @Test
    fun `every pool in the app is valid for exams given in October 2026`() {
        val day = LocalDate.of(2026, 10, 31)
        for (license in LicenseClass.entries) {
            val pool = realPools.load(license)
            assertTrue(license.name, !day.isBefore(pool.validFrom) && !day.isAfter(pool.validThrough))
        }
    }

    @Test
    fun `withdrawn questions are not in the pools`() {
        val withdrawn = setOf("G1A04", "G1C08", "G1C09", "G1C10", "G1E09", "G6B09", "G8C01", "G9C06", "G9D13", "E2A13", "E4D05", "E6D07", "E9E10")
        val ids = LicenseClass.entries.flatMap { realPools.load(it).questions.map { q -> q.id } }.toSet()
        assertFalse((withdrawn intersect ids).isNotEmpty())
    }

    @Test
    fun `every referenced figure ships as an asset`() {
        for (license in LicenseClass.entries) {
            realPools.load(license).questions.mapNotNull { it.figure }.toSet().forEach { fig ->
                assertTrue("missing figure $fig", File("src/main/assets/figures/$fig.png").exists())
            }
        }
    }
}
