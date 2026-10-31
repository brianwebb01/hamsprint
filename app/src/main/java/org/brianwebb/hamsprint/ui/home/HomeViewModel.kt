package org.brianwebb.hamsprint.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
import org.brianwebb.hamsprint.domain.QuestionPool
import org.brianwebb.hamsprint.domain.Readiness
import org.brianwebb.hamsprint.domain.ReadinessReport

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Ready(
        val license: LicenseClass,
        val pool: QuestionPool,
        val report: ReadinessReport,
        val recentExams: List<ExamRecord>,
        val studyTimeMillis: Long,
    ) : HomeUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    // Due counts and decay depend on the clock, so the screen re-evaluates on resume.
    private val refresh = MutableStateFlow(0)

    val state: StateFlow<HomeUiState> = combine(
        container.settings.settings.map { it.license }.filterNotNull().distinctUntilChanged(),
        container.progress.version,
        refresh,
    ) { license, _, _ -> license }
        // Errors are caught per load so the flow survives and "Try again" can re-run it.
        .mapLatest { license ->
            runCatching { load(license) }
                .getOrElse { HomeUiState.Error(it.message ?: "Couldn't load your progress.") }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    fun refresh() {
        refresh.value++
    }

    private suspend fun load(license: LicenseClass): HomeUiState.Ready {
        val pool = withContext(Dispatchers.IO) { container.pools.load(license) }
        val progress = container.progress.progress(license)
        val report = withContext(Dispatchers.Default) {
            Readiness.evaluate(pool, progress, container.clock.nowMillis())
        }
        return HomeUiState.Ready(
            license = license,
            pool = pool,
            report = report,
            recentExams = container.progress.recentExams(license, limit = 5),
            studyTimeMillis = container.progress.studyTimeMillis(license),
        )
    }
}
