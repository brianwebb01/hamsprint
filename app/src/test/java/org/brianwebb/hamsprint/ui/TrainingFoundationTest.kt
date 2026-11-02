package org.brianwebb.hamsprint.ui

import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.brianwebb.hamsprint.AppContainer
import org.brianwebb.hamsprint.domain.CardKind
import org.brianwebb.hamsprint.domain.LicenseClass
import org.brianwebb.hamsprint.ui.training.CardPhase
import org.brianwebb.hamsprint.ui.training.TrainingUiState
import org.brianwebb.hamsprint.ui.training.TrainingViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.random.Random

/** Training opens with a foundation, then the questions that build on it, and never teaches it twice. */
@RunWith(AndroidJUnit4::class)
class TrainingFoundationTest {
    private val container = AppContainer(ApplicationProvider.getApplicationContext(), random = Random(7), progressDbName = null)
    private val stores = mutableListOf<ViewModelStore>()

    @After
    fun tearDown() {
        stores.forEach { it.clear() }
        container.progress.close()
    }

    private fun start(): TrainingViewModel {
        container.settings.update { it.copy(license = LicenseClass.TECHNICIAN, autoAdvance = false) }
        val store = ViewModelStore().also { stores += it }
        return ViewModelProvider.create(store, viewModelFactory { initializer { TrainingViewModel(container, null) } })[TrainingViewModel::class]
    }

    @Test
    fun `the first foundation is taught before its questions, which then show it as a reminder`() {
        val vm = start()
        val curriculum = container.foundations.load(LicenseClass.TECHNICIAN)
        val lesson = await(vm) { it as? TrainingUiState.Lesson }
        assertEquals(curriculum.foundations.first(), lesson.foundation)
        assertEquals(1, lesson.number)

        vm.startLesson()
        val first = await(vm) { (it as? TrainingUiState.Studying)?.takeIf { s -> s.phase == CardPhase.Asking } }
        assertEquals(CardKind.PREVIEW, first.card.kind)
        assertEquals(lesson.foundation.questionIds.first(), first.card.question.id)
        assertEquals(lesson.foundation, first.foundation)
        assertTrue("reminder shows on the preview", first.foundationVisible)
        assertTrue(runBlocking { container.progress.seenFoundations(LicenseClass.TECHNICIAN) }.contains(lesson.foundation.id))

        // Its other questions follow in order, and the reminder is hidden while asking.
        val introduced = mutableListOf(first.card.question.id)
        vm.acknowledgePreview()
        while (introduced.size < lesson.foundation.questionIds.size) {
            val s = await(vm) { (it as? TrainingUiState.Studying)?.takeIf { s -> s.phase == CardPhase.Asking } }
            if (s.card.kind == CardKind.PREVIEW) {
                introduced += s.card.question.id
                vm.acknowledgePreview()
            } else {
                assertFalse("hidden while asking", s.foundationVisible)
                vm.select(s.card.correctPosition)
                vm.next()
            }
        }
        assertEquals(lesson.foundation.questionIds, introduced)
    }

    @Test
    fun `a foundation already taught is not taught again in a new session`() {
        val first = start()
        val taught = await(first) { it as? TrainingUiState.Lesson }.foundation
        first.startLesson()
        await(first) { it as? TrainingUiState.Studying }

        val second = start()
        val next = await(second) { it as? TrainingUiState.Lesson ?: it as? TrainingUiState.Studying }
        if (next is TrainingUiState.Lesson) assertNotEquals(taught, next.foundation)
    }

    private fun <T : Any> await(vm: TrainingViewModel, pick: (TrainingUiState) -> T?): T {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            pick(vm.state.value)?.let { return it }
            Thread.sleep(10)
        }
        error("unexpected state: ${vm.state.value}")
    }
}
