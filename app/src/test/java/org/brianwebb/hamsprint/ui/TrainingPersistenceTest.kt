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
import org.brianwebb.hamsprint.domain.LicenseClass
import org.brianwebb.hamsprint.ui.training.TrainingUiState
import org.brianwebb.hamsprint.ui.training.TrainingViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class TrainingPersistenceTest {
    @Test
    fun `answers survive leaving training immediately after answering`() {
        val container = AppContainer(ApplicationProvider.getApplicationContext(), progressDbName = null)
        container.settings.update { it.copy(license = LicenseClass.GENERAL) }
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(store, viewModelFactory { initializer { TrainingViewModel(container, null) } })[TrainingViewModel::class]

        val previewed = mutableListOf<String>()
        repeat(3) {
            val studying = awaitStudying(vm)
            previewed += studying.card.question.id
            vm.acknowledgePreview()
        }
        // Close the screen at once: the ViewModel (and its scope) are cleared.
        store.clear()

        val saved = runBlocking { container.progress.progress(LicenseClass.GENERAL) }
        assertEquals(previewed.toSet(), saved.keys)
        container.progress.close()
    }

    private fun awaitStudying(vm: TrainingViewModel): TrainingUiState.Studying {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            (vm.state.value as? TrainingUiState.Studying)?.let { return it }
            Thread.sleep(10)
        }
        error("training never started: ${vm.state.value}")
    }
}
