package org.brianwebb.hamsprint.ui

import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.brianwebb.hamsprint.AppContainer
import org.brianwebb.hamsprint.domain.CardKind
import org.brianwebb.hamsprint.domain.LicenseClass
import org.brianwebb.hamsprint.ui.training.CardPhase
import org.brianwebb.hamsprint.ui.training.TrainingUiState
import org.brianwebb.hamsprint.ui.training.TrainingViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.random.Random

/** Visual aids appear on a question's preview and after a miss, never while it is being asked. */
@RunWith(AndroidJUnit4::class)
class TrainingAidTest {
    private val container = AppContainer(ApplicationProvider.getApplicationContext(), random = Random(42), progressDbName = null)
    private val store = ViewModelStore()

    @After
    fun tearDown() {
        store.clear()
        container.progress.close()
    }

    private fun start(autoAdvance: Boolean): TrainingViewModel {
        container.settings.update { it.copy(license = LicenseClass.TECHNICIAN, autoAdvance = autoAdvance) }
        return ViewModelProvider.create(store, viewModelFactory { initializer { TrainingViewModel(container, null) } })[TrainingViewModel::class]
    }

    @Test
    fun `aids show on previews and misses but not while asking`() {
        val vm = start(autoAdvance = false)
        val aids = container.aids.load(LicenseClass.TECHNICIAN)
        var previewsWithAid = 0
        var missesWithAid = 0
        // Study until both a preview and a miss have shown an aid (bounded so a regression fails fast).
        var cards = 0
        while ((previewsWithAid == 0 || missesWithAid == 0) && cards++ < 120) {
            val s = awaitStudying(vm)
            assertEquals(aids[s.card.question.id], s.aid)
            if (s.card.kind == CardKind.PREVIEW) {
                assertEquals(s.aid != null, s.aidVisible)
                if (s.aid != null) previewsWithAid++
                vm.acknowledgePreview()
            } else {
                assertFalse("hidden while asking", s.aidVisible)
                vm.select((s.card.correctPosition + 1) % 4)
                val revealed = vm.state.value as TrainingUiState.Studying
                assertTrue((revealed.phase as CardPhase.Revealed).showAid)
                assertEquals(s.aid != null, revealed.aidVisible)
                if (s.aid != null) missesWithAid++
                vm.next()
            }
        }
        assertTrue("saw an aid on a preview ($previewsWithAid) and on a miss ($missesWithAid)", previewsWithAid > 0 && missesWithAid > 0)
    }

    @Test
    fun `a correct answer skips the aid even without auto advance`() {
        val vm = start(autoAdvance = false)
        while (awaitStudying(vm).card.kind == CardKind.PREVIEW) vm.acknowledgePreview()
        val s = awaitStudying(vm)
        vm.select(s.card.correctPosition)
        val revealed = vm.state.value as TrainingUiState.Studying
        assertFalse((revealed.phase as CardPhase.Revealed).showAid)
        assertFalse(revealed.aidVisible)
    }

    private fun awaitStudying(vm: TrainingViewModel): TrainingUiState.Studying {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            (vm.state.value as? TrainingUiState.Studying)?.takeIf { it.phase == CardPhase.Asking }?.let { return it }
            Thread.sleep(10)
        }
        error("no card: ${vm.state.value}")
    }
}
