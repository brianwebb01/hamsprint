package org.brianwebb.hamsprint.ui.foundations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brianwebb.hamsprint.AppContainer
import org.brianwebb.hamsprint.domain.Foundation
import org.brianwebb.hamsprint.domain.MemoryModel

/** One foundation in the library, with how far the learner has got through its questions. */
data class FoundationItem(
    val foundation: Foundation,
    val number: Int,
    val seen: Boolean,
    val learned: Int,
    val figures: List<String>,
) {
    val total: Int get() = foundation.questionIds.size
}

sealed interface FoundationsUiState {
    data object Loading : FoundationsUiState
    data class Error(val message: String) : FoundationsUiState
    data class Ready(val items: List<FoundationItem>) : FoundationsUiState {
        val taught: Int get() = items.count { it.seen }
    }
}

/** The library of a pool's foundations, readable any time, in curriculum order. */
class FoundationsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<FoundationsUiState>(FoundationsUiState.Loading)
    val state: StateFlow<FoundationsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = FoundationsUiState.Loading
        viewModelScope.launch {
            _state.value = runCatching {
                val license = checkNotNull(container.settings.settings.value.license) { "No exam selected" }
                val curriculum = withContext(Dispatchers.IO) { container.foundations.load(license) }
                val pool = withContext(Dispatchers.IO) { container.pools.load(license) }
                val progress = container.progress.progress(license)
                val seen = container.progress.seenFoundations(license)
                FoundationsUiState.Ready(
                    curriculum.foundations.mapIndexed { i, f ->
                        FoundationItem(
                            foundation = f,
                            number = i + 1,
                            seen = f.id in seen,
                            learned = f.questionIds.count { (progress[it]?.stage ?: 0) > MemoryModel.LEARNING },
                            figures = f.figures(pool),
                        )
                    },
                )
            }.getOrElse { FoundationsUiState.Error(it.message ?: "Couldn't load the foundations.") }
        }
    }
}
