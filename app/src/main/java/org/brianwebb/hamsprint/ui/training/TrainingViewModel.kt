package org.brianwebb.hamsprint.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brianwebb.hamsprint.AppContainer
import org.brianwebb.hamsprint.domain.Card
import org.brianwebb.hamsprint.domain.LicenseClass
import org.brianwebb.hamsprint.domain.QuestionPool
import org.brianwebb.hamsprint.domain.QuestionProgress
import org.brianwebb.hamsprint.domain.Readiness
import org.brianwebb.hamsprint.domain.RoundSummary
import org.brianwebb.hamsprint.domain.TrainingConfig
import org.brianwebb.hamsprint.domain.TrainingSession

sealed interface CardPhase {
    data object Asking : CardPhase
    data class Revealed(val chosen: Int, val correct: Boolean, val correctPosition: Int) : CardPhase
}

sealed interface TrainingUiState {
    data object Loading : TrainingUiState
    data class Error(val message: String) : TrainingUiState
    data class Studying(
        val card: Card,
        val phase: CardPhase,
        val cardsShown: Int,
        val roundLength: Int,
        val scopeTitle: String?,
    ) : TrainingUiState
    data class Summary(
        val summary: RoundSummary,
        val passBefore: Double,
        val passAfter: Double,
        val scopeTitle: String?,
    ) : TrainingUiState
}

class TrainingViewModel(
    private val container: AppContainer,
    private val subelementId: String?,
) : ViewModel() {
    private val _state = MutableStateFlow<TrainingUiState>(TrainingUiState.Loading)
    val state: StateFlow<TrainingUiState> = _state.asStateFlow()

    private lateinit var license: LicenseClass
    private lateinit var pool: QuestionPool
    private var session: TrainingSession? = null
    private var passBefore = 0.0
    private var cardShownAt = 0L
    private var autoAdvance: Job? = null

    private val scopeTitle: String?
        get() = subelementId?.let { id -> pool.subelements.firstOrNull { it.id == id }?.title }

    init {
        startRound()
    }

    fun startRound() {
        _state.value = TrainingUiState.Loading
        viewModelScope.launch {
            runCatching {
                val settings = container.settings.settings.value
                license = checkNotNull(settings.license) { "No exam selected" }
                pool = withContext(Dispatchers.IO) { container.pools.load(license) }
                val progress = container.progress.progress(license)
                val scope = subelementId
                    ?.let { id -> pool.subelements.firstOrNull { it.id == id }?.questions }
                    ?: pool.questions
                passBefore = pass(progress)
                TrainingSession(
                    pool = pool,
                    scope = scope,
                    initialProgress = progress,
                    clock = container.clock,
                    random = container.random,
                    config = TrainingConfig(previewNew = settings.previewNew, shuffleAnswers = settings.shuffleAnswers),
                )
            }.onSuccess {
                session = it
                publish()
            }.onFailure { fail(it) }
        }
    }

    fun acknowledgePreview() {
        val s = session ?: return
        val updated = s.acknowledgePreview()
        persist(updated)
        publish()
    }

    fun select(position: Int) {
        val s = session ?: return
        val current = _state.value as? TrainingUiState.Studying ?: return
        if (current.phase != CardPhase.Asking) return
        val result = s.answer(position)
        persist(result.progress)
        _state.value = current.copy(phase = CardPhase.Revealed(position, result.correct, result.correctPosition))
        if (result.correct && container.settings.settings.value.autoAdvance) {
            autoAdvance = viewModelScope.launch {
                delay(AUTO_ADVANCE_MILLIS)
                next()
            }
        }
    }

    fun next() {
        autoAdvance?.cancel()
        autoAdvance = null
        val s = session ?: return
        if ((_state.value as? TrainingUiState.Studying)?.phase !is CardPhase.Revealed) return
        s.advance()
        publish()
    }

    private fun publish() {
        val s = session ?: return
        val card = s.current
        if (card == null) {
            _state.value = TrainingUiState.Summary(s.summary(), passBefore, pass(s.progress), scopeTitle)
            return
        }
        cardShownAt = container.clock.nowMillis()
        _state.value = TrainingUiState.Studying(card, CardPhase.Asking, s.cardsShown, s.roundLength, scopeTitle)
    }

    private fun persist(progress: QuestionProgress) {
        // Time on a card counts as study time, capped so a phone left on the table doesn't inflate it.
        val studied = (container.clock.nowMillis() - cardShownAt).coerceIn(0, MAX_CARD_MILLIS)
        // Undispatched: the write takes its place in the store's FIFO queue before
        // this returns, and the app scope lets it finish after the screen closes.
        container.appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runCatching { container.progress.save(license, listOf(progress), studied) }
                .onFailure { fail(it) }
        }
    }

    private fun pass(progress: Map<String, QuestionProgress>): Double =
        Readiness.evaluate(pool, progress, container.clock.nowMillis()).passProbability

    private fun fail(t: Throwable) {
        _state.value = TrainingUiState.Error(t.message ?: "Something went wrong saving your progress.")
    }

    private companion object {
        const val AUTO_ADVANCE_MILLIS = 650L
        const val MAX_CARD_MILLIS = 60_000L
    }
}
