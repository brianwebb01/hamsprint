package org.brianwebb.hamsprint.ui.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brianwebb.hamsprint.AppContainer
import org.brianwebb.hamsprint.data.ExamRecord
import org.brianwebb.hamsprint.domain.Exam
import org.brianwebb.hamsprint.domain.ExamResult
import org.brianwebb.hamsprint.domain.LicenseClass

sealed interface ExamUiState {
    data object Loading : ExamUiState
    data class Error(val message: String) : ExamUiState
    data class Taking(
        val exam: Exam,
        val index: Int,
        /** Card index → chosen display position. */
        val answers: Map<Int, Int>,
        val flagged: Set<Int>,
        val startedAtMillis: Long,
        val submitting: Boolean = false,
    ) : ExamUiState {
        val unanswered: Int get() = exam.cards.size - answers.size
    }
    data class Finished(val result: ExamResult, val durationMillis: Long) : ExamUiState
}

class ExamViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<ExamUiState>(ExamUiState.Loading)
    val state: StateFlow<ExamUiState> = _state.asStateFlow()
    private lateinit var license: LicenseClass

    init {
        viewModelScope.launch {
            runCatching {
                val settings = container.settings.settings.value
                license = checkNotNull(settings.license) { "No exam selected" }
                val pool = withContext(Dispatchers.IO) { container.pools.load(license) }
                Exam.generate(pool, container.random, settings.shuffleAnswers)
            }.onSuccess { exam ->
                _state.value = ExamUiState.Taking(exam, 0, emptyMap(), emptySet(), container.clock.nowMillis())
            }.onFailure { _state.value = ExamUiState.Error(it.message ?: "Couldn't build an exam.") }
        }
    }

    fun select(position: Int) = updateTaking { it.copy(answers = it.answers + (it.index to position)) }

    fun goTo(index: Int) = updateTaking { it.copy(index = index.coerceIn(0, it.exam.cards.lastIndex)) }

    fun next() = updateTaking { it.copy(index = (it.index + 1).coerceAtMost(it.exam.cards.lastIndex)) }

    fun previous() = updateTaking { it.copy(index = (it.index - 1).coerceAtLeast(0)) }

    fun toggleFlag() = updateTaking {
        it.copy(flagged = if (it.index in it.flagged) it.flagged - it.index else it.flagged + it.index)
    }

    fun submit() {
        val taking = _state.value as? ExamUiState.Taking ?: return
        if (taking.submitting) return
        _state.value = taking.copy(submitting = true)
        container.appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val now = container.clock.nowMillis()
            val duration = now - taking.startedAtMillis
            runCatching {
                val result = taking.exam.grade(taking.answers)
                val progress = container.progress.progress(license)
                container.progress.recordExam(
                    license,
                    ExamRecord(now, result.score, result.total, result.passingScore, duration),
                    result.applyTo(progress, now),
                )
                result
            }.onSuccess { _state.value = ExamUiState.Finished(it, duration) }
                .onFailure { _state.value = ExamUiState.Error(it.message ?: "Couldn't save your exam.") }
        }
    }

    private fun updateTaking(transform: (ExamUiState.Taking) -> ExamUiState.Taking) {
        _state.update { s -> if (s is ExamUiState.Taking && !s.submitting) transform(s) else s }
    }
}
