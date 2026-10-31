package org.brianwebb.hamsprint.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.brianwebb.hamsprint.domain.LicenseClass
import org.brianwebb.hamsprint.domain.QuestionProgress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressStoreTest {
    private val store = ProgressStore(ApplicationProvider.getApplicationContext(), Dispatchers.Unconfined, name = null)

    @After fun tearDown() = store.close()

    private fun p(id: String, stage: Int = 1) = QuestionProgress(id, stage, 100L, 2, 1, 0, 50L)

    @Test
    fun `empty store has no progress, exams, or study time`() = runTest {
        assertTrue(store.progress(LicenseClass.GENERAL).isEmpty())
        assertTrue(store.recentExams(LicenseClass.GENERAL).isEmpty())
        assertEquals(0L, store.studyTimeMillis(LicenseClass.GENERAL))
    }

    @Test
    fun `saving is an upsert and is scoped per license`() = runTest {
        store.save(LicenseClass.GENERAL, listOf(p("G1A01"), p("G1A02")))
        store.save(LicenseClass.GENERAL, listOf(p("G1A01", stage = 4)))
        store.save(LicenseClass.TECHNICIAN, listOf(p("T1A01")))

        val general = store.progress(LicenseClass.GENERAL)
        assertEquals(setOf("G1A01", "G1A02"), general.keys)
        assertEquals(4, general.getValue("G1A01").stage)
        assertEquals(setOf("T1A01"), store.progress(LicenseClass.TECHNICIAN).keys)
    }

    @Test
    fun `saving the same update twice is idempotent for progress`() = runTest {
        val update = listOf(p("G1A01", stage = 2))
        store.save(LicenseClass.GENERAL, update)
        store.save(LicenseClass.GENERAL, update)
        assertEquals(mapOf("G1A01" to update[0]), store.progress(LicenseClass.GENERAL))
    }

    @Test
    fun `study time accumulates and exams are newest first and bounded`() = runTest {
        store.save(LicenseClass.GENERAL, emptyList(), studiedMillis = 1_000)
        store.save(LicenseClass.GENERAL, emptyList(), studiedMillis = 2_500)
        repeat(4) { i ->
            store.recordExam(LicenseClass.GENERAL, ExamRecord(i * 10L, 20 + i, 35, 26, 60_000), listOf(p("G1B0$i")))
        }
        assertEquals(3_500L + 4 * 60_000, store.studyTimeMillis(LicenseClass.GENERAL))
        val exams = store.recentExams(LicenseClass.GENERAL, limit = 3)
        assertEquals(listOf(23, 22, 21), exams.map { it.score })
        assertEquals(4, store.progress(LicenseClass.GENERAL).size)
    }

    @Test
    fun `reset clears only the chosen license and bumps the version`() = runTest {
        store.save(LicenseClass.GENERAL, listOf(p("G1A01")), studiedMillis = 10)
        store.save(LicenseClass.EXTRA, listOf(p("E1A01")))
        val before = store.version.value
        store.reset(LicenseClass.GENERAL)
        assertTrue(store.progress(LicenseClass.GENERAL).isEmpty())
        assertEquals(0L, store.studyTimeMillis(LicenseClass.GENERAL))
        assertEquals(1, store.progress(LicenseClass.EXTRA).size)
        assertTrue(store.version.value > before)
    }
}
