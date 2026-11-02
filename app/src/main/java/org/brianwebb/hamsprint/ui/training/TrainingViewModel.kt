package org.brianwebb.hamsprint.ui.training

import android.util.Log
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
import org.brianwebb.hamsprint.domain.CardKind
import org.brianwebb.hamsprint.domain.Curriculum
import org.brianwebb.hamsprint.domain.Foundation
import org.brianwebb.hamsprint.domain.LicenseClass
import org.brianwebb.hamsprint.domain.QuestionPool
import org.brianwebb.hamsprint.domain.QuestionProgress
import org.brianwebb.hamsprint.domain.Readiness
import org.brianwebb.hamsprint.domain.RoundSummary
import org.brianwebb.hamsprint.domain.TrainingConfig
import org.brianwebb.hamsprint.domain.TrainingSession
import org.brianwebb.hamsprint.domain.VisualAid

sealed interface CardPhase {
    data object Asking : CardPhase
    data class Revealed(
        val chosen: Int,
        val correct: Boolean,
        val correctPosition: Int,
        /** Aids return only after a miss; a correct answer moves on without one. */
        val showAid: Boolean,
    ) : CardPhase
}

sealed interface TrainingUiState {
    data object Loading : TrainingUiState
    data class Error(val message: String) : TrainingUiState
    /** A foundation taught before the first question that builds on it. */
    data class Lesson(
        val foundation: Foundation,
        val number: Int,
        val total: Int,
        /** Pool figures the foundation's questions use, shown with its rows. */
        val figures: List<String>,
        val cardsShown: Int,
        val roundLength: Int,
    ) : TrainingUiState

    data class Studying(
        val card: Card,
        /** The question's visual aid, shown on its preview and after a miss. */
        val aid: VisualAid?,
        val phase: CardPhase,
        val cardsShown: Int,
        val roundLength: Int,
        val scopeTitle: String?,
        /** The framework this question builds on, recalled alongside the aid. */
        val foundation: Foundation? = null,
    ) : TrainingUiState {
        /** Help shows on a preview and after a miss, never while the learner is being asked. */
        private val helping: Boolean
            get() = card.kind == CardKind.PREVIEW || (phase as? CardPhase.Revealed)?.showAid == true
        val aidVisible: Boolean get() = aid != null && helping
        val foundationVisible: Boolean get() = foundation != null && helping
    }
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
    private var aids: Map<String, VisualAid> = emptyMap()
    private var curriculum: Curriculum = Curriculum.EMPTY
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
                aids = loadAids(license)
                curriculum = loadCurriculum(license)
                val progress = container.progress.progress(license)
                val seen = container.progress.seenFoundations(license)
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
                    curriculum = curriculum,
                    seenFoundations = seen,
                )
            }.onSuccess {
                session = it
                publish()
            }.onFailure { fail(it) }
        }
    }

    /** The learner has read the foundation; its questions follow. */
    fun startLesson() {
        val s = session ?: return
        if (_state.value !is TrainingUiState.Lesson) return
        val id = s.acknowledgeLesson()
        val at = container.clock.nowMillis()
        // Reading a lesson is study time too; capped like a card, but a lesson is longer.
        val studied = (at - cardShownAt).coerceIn(0, MAX_LESSON_MILLIS)
        container.appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runCatching { container.progress.markFoundationSeen(license, id, at, studied) }
                .onFailure { fail(it) }
        }
        publish()
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
        val autoAdvancing = result.correct && container.settings.settings.value.autoAdvance
        _state.value = current.copy(
            phase = CardPhase.Revealed(position, result.correct, result.correctPosition, showAid = !result.correct),
        )
        if (autoAdvancing) {
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
        val lesson = s.lesson
        _state.value = if (lesson != null) {
            TrainingUiState.Lesson(
                lesson, curriculum.numberOf(lesson), curriculum.foundations.size, lesson.figures(pool), s.cardsShown - 1, s.roundLength,
            )
        } else {
            TrainingUiState.Studying(
                card, aids[card.question.id], CardPhase.Asking, s.cardsShown, s.roundLength, scopeTitle,
                curriculum.foundationOf(card.question.id),
            )
        }
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

    /** Aids only enrich training, so a bad aid file degrades to plain cards instead of blocking study. */
    private suspend fun loadAids(license: LicenseClass): Map<String, VisualAid> =
        runCatching { withContext(Dispatchers.IO) { container.aids.load(license) } }
            .onFailure { Log.e(TAG, "Visual aids unavailable for ${license.poolId}", it) }
            .getOrDefault(emptyMap())

    /** Foundations only structure training, so a bad file degrades to the plain order instead of blocking study. */
    private suspend fun loadCurriculum(license: LicenseClass): Curriculum =
        runCatching { withContext(Dispatchers.IO) { container.foundations.load(license) } }
            .onFailure { Log.e(TAG, "Foundations unavailable for ${license.poolId}", it) }
            .getOrDefault(Curriculum.EMPTY)

    private fun pass(progress: Map<String, QuestionProgress>): Double =
        Readiness.evaluate(pool, progress, container.clock.nowMillis()).passProbability

    private fun fail(t: Throwable) {
        _state.value = TrainingUiState.Error(t.message ?: "Something went wrong saving your progress.")
    }

    private companion object {
        const val TAG = "Training"
        const val AUTO_ADVANCE_MILLIS = 650L
        const val MAX_CARD_MILLIS = 60_000L
        const val MAX_LESSON_MILLIS = 180_000L
    }
}
