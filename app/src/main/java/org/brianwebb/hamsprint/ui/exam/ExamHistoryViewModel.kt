package org.brianwebb.hamsprint.ui.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.brianwebb.hamsprint.AppContainer
import org.brianwebb.hamsprint.data.ExamRecord
import org.brianwebb.hamsprint.domain.LicenseClass
import org.brianwebb.hamsprint.domain.Readiness

/** How practice-exam history bears on exam readiness. */
data class ExamHistorySummary(
    /** Newest first. */
    val attempts: List<ExamRecord>,
    val total: Int,
    val passingScore: Int,
    val passProbability: Double,
) {
    val recent: List<ExamRecord> get() = attempts.take(RECENT)
    val recentAverage: Double? get() = recent.takeIf { it.isNotEmpty() }?.map { it.score }?.average()
    val best: ExamRecord? get() = attempts.maxByOrNull { it.score }
    val passStreak: Int get() = attempts.takeWhile { it.passed }.size
    val passRate: Double? get() = attempts.takeIf { it.isNotEmpty() }?.let { a -> a.count { it.passed }.toDouble() / a.size }

    /** Ready when the memory model says so AND the learner has proven it twice in a row. */
    val examReady: Boolean get() = passProbability >= Readiness.READY_THRESHOLD && passStreak >= CONFIRMING_PASSES

    companion object {
        const val RECENT = 5
        const val CONFIRMING_PASSES = 2
        const val HISTORY_LIMIT = 50
    }
}

sealed interface ExamHistoryUiState {
    data object Loading : ExamHistoryUiState
    data class Error(val message: String) : ExamHistoryUiState
    data class Ready(val license: LicenseClass, val summary: ExamHistorySummary) : ExamHistoryUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExamHistoryViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<ExamHistoryUiState> = combine(
        container.settings.settings.map { it.license }.filterNotNull().distinctUntilChanged(),
        container.progress.version,
    ) { license, _ -> license }
        .mapLatest<LicenseClass, ExamHistoryUiState> { license ->
            val pool = withContext(Dispatchers.IO) { container.pools.load(license) }
            val progress = container.progress.progress(license)
            val pass = withContext(Dispatchers.Default) {
                Readiness.evaluate(pool, progress, container.clock.nowMillis()).passProbability
            }
            ExamHistoryUiState.Ready(
                license,
                ExamHistorySummary(
                    attempts = container.progress.recentExams(license, ExamHistorySummary.HISTORY_LIMIT),
                    total = pool.examQuestions,
                    passingScore = pool.passingScore,
                    passProbability = pass,
                ),
            )
        }
        .catch { emit(ExamHistoryUiState.Error(it.message ?: "Couldn't load your exam history.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExamHistoryUiState.Loading)
}
