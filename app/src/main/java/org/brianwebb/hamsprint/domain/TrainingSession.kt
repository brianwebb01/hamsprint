package org.brianwebb.hamsprint.domain

import kotlin.random.Random

/** Wall-clock source, injected so scheduling is testable. */
fun interface Clock {
    fun nowMillis(): Long
}

data class TrainingConfig(
    /** Cards per round; a round ends with a short summary, then the learner can keep going. */
    val roundLength: Int = 20,
    /** How many freshly introduced questions may be "in flight" at once (working memory). */
    val workingSetSize: Int = 5,
    /** Show a new question with its answer before asking for it (errorless first exposure). */
    val previewNew: Boolean = true,
    val shuffleAnswers: Boolean = true,
    /** When nothing is due or new, keep practicing the weakest questions instead of stopping. */
    val reviewAhead: Boolean = true,
)

enum class CardKind {
    /** First exposure: question with its answer revealed. */
    PREVIEW,
    /** Recall of a question being learned this round (just previewed or just missed). */
    LEARNING,
    /** First exposure without a preview: the learner guesses. */
    NEW,
    /** Spaced review that is due. */
    REVIEW,
    /** Extra practice on a question that is not yet due. */
    AHEAD,
}

/**
 * One screen of training. [answerOrder] maps display position to the index in
 * [Question.answers], so shuffled presentation never changes what is correct.
 */
data class Card(val question: Question, val kind: CardKind, val answerOrder: List<Int>) {
    val correctPosition: Int get() = answerOrder.indexOf(question.correctIndex)
    fun answerAt(position: Int): String = question.answers[answerOrder[position]]
}

data class AnswerResult(val correct: Boolean, val correctPosition: Int, val progress: QuestionProgress)

data class RoundSummary(
    val answered: Int,
    val correct: Int,
    val introduced: Int,
    val reviewed: Int,
    /** True when the round ended early because nothing was left to study in scope. */
    val caughtUp: Boolean,
)

/**
 * One round of adaptive training over [scope].
 *
 * Order of business on every card:
 *  1. a question being learned whose spacing gap has elapsed,
 *  2. a brand-new question, if one hasn't been introduced for a few cards,
 *     so a review backlog never stalls coverage of the pool,
 *  3. a spaced review that is due,
 *  4. a brand-new question, if working memory has room, highest exam weight first,
 *  5. any question being learned (to avoid stalling),
 *  6. optional review-ahead of the weakest questions,
 *  7. as a last resort, the question just shown (only when nothing else exists).
 *
 * A previewed (or missed) question must be recalled correctly once, a few cards
 * later, to graduate to the spaced schedule, whose first review is minutes
 * away. Every question therefore gets at least two spaced retrievals before it
 * counts toward readiness, at a cost of about four cards.
 */
class TrainingSession(
    pool: QuestionPool,
    scope: List<Question>,
    initialProgress: Map<String, QuestionProgress>,
    private val clock: Clock,
    private val random: Random,
    private val config: TrainingConfig = TrainingConfig(),
) {
    private class WorkItem(var remaining: Int, var notBefore: Int)

    private val progressMap = initialProgress.toMutableMap()
    private val scopeQuestions = scope
    private val scopeById = scope.associateBy { it.id }
    private val working = LinkedHashMap<String, WorkItem>()
    private val newQueue: ArrayDeque<Question>
    private var step = 0
    private var lastQuestionId: String? = null
    private var answered = 0
    private var correct = 0
    private var introduced = 0
    private var reviewed = 0
    private var caughtUp = false
    private var answeredCurrent = false
    private var cardsSinceNew = 0

    var current: Card? = null
        private set

    val progress: Map<String, QuestionProgress> get() = progressMap
    val cardsShown: Int get() = step
    val roundLength: Int get() = config.roundLength

    init {
        val groupSize = pool.groups.associate { it.id to it.questions.size }
        // Highest exam weight (smallest group) first; shuffling within a weight
        // band interleaves topics, which improves discrimination between them.
        newQueue = ArrayDeque(
            scope.filter { it.id !in progressMap }
                .shuffled(random)
                .sortedBy { groupSize.getValue(it.groupId) },
        )
        // Questions left in learning by an earlier round or a missed exam question.
        scope.filter { progressMap[it.id]?.stage == MemoryModel.LEARNING }
            .sortedBy { progressMap.getValue(it.id).lastAnsweredAtMillis }
            .forEach { working[it.id] = WorkItem(remaining = 1, notBefore = 0) }
        current = nextCard()
    }

    val isFinished: Boolean get() = current == null

    fun summary(): RoundSummary = RoundSummary(answered, correct, introduced, reviewed, caughtUp)

    /** The learner has read a preview card; it now needs to be recalled. */
    fun acknowledgePreview(): QuestionProgress {
        val card = checkNotNull(current) { "No current card" }
        check(card.kind == CardKind.PREVIEW) { "Current card is not a preview" }
        val now = clock.nowMillis()
        val p = QuestionProgress(card.question.id, MemoryModel.LEARNING, now, 0, 0, 0, now)
        progressMap[card.question.id] = p
        working[card.question.id] = WorkItem(remaining = 1, notBefore = step + RECALL_GAP)
        introduced++
        advance()
        return p
    }

    /** Grades the answer at display [position] of the current card. Call [advance] to continue. */
    fun answer(position: Int): AnswerResult {
        val card = checkNotNull(current) { "No current card" }
        check(card.kind != CardKind.PREVIEW) { "Preview cards are acknowledged, not answered" }
        check(!answeredCurrent) { "Card ${card.question.id} was already answered" }
        answeredCurrent = true
        val id = card.question.id
        val isCorrect = card.answerOrder[position] == card.question.correctIndex
        val now = clock.nowMillis()
        val before = progressMap[id]
        answered++
        if (isCorrect) correct++
        if (card.kind == CardKind.NEW) introduced++

        val after = when {
            !isCorrect -> {
                working[id] = WorkItem(remaining = 1, notBefore = step + RELEARN_GAP)
                MemoryModel.lapse(before, id, now)
            }
            card.kind == CardKind.REVIEW -> {
                reviewed++
                MemoryModel.promote(before, id, now)
            }
            card.kind == CardKind.AHEAD -> MemoryModel.confirm(before, id, now)
            card.kind == CardKind.NEW -> {
                // A correct first guess earns one confirming recall, not a free pass.
                working[id] = WorkItem(remaining = 1, notBefore = step + RECALL_GAP)
                MemoryModel.confirm(before, id, now)
            }
            else -> { // LEARNING
                val item = working.getValue(id)
                item.remaining--
                if (item.remaining <= 0) {
                    working.remove(id)
                    MemoryModel.promote(before, id, now)
                } else {
                    item.notBefore = step + 4
                    MemoryModel.confirm(before, id, now)
                }
            }
        }
        progressMap[id] = after
        return AnswerResult(isCorrect, card.correctPosition, after)
    }

    /** Moves past an answered card. Ends the round once [roundLength] cards have been shown. */
    fun advance() {
        val card = current ?: return
        check(card.kind == CardKind.PREVIEW || answeredCurrent) { "Answer the card before advancing" }
        answeredCurrent = false
        current = if (step >= config.roundLength) null else nextCard()
    }

    private fun nextCard(): Card? {
        val now = clock.nowMillis()
        val picked = pickReadyWorking()
            ?: (if (cardsSinceNew >= NEW_CADENCE) pickNew() else null)
            ?: pickDueReview(now)
            ?: pickNew()
            ?: pickAnyWorking(allowRepeat = false)
            ?: pickAhead(now)
            ?: pickAnyWorking(allowRepeat = true)
        if (picked == null) {
            caughtUp = true
            return null
        }
        val (question, kind) = picked
        cardsSinceNew = if (kind == CardKind.PREVIEW || kind == CardKind.NEW) 0 else cardsSinceNew.saturatingInc()
        step++
        lastQuestionId = question.id
        val order = if (config.shuffleAnswers) question.answers.indices.shuffled(random) else question.answers.indices.toList()
        return Card(question, kind, order)
    }

    private fun questionFor(id: String): Question = scopeById.getValue(id)

    private fun pickReadyWorking(): Pair<Question, CardKind>? =
        working.entries
            .filter { it.value.notBefore <= step && it.key != lastQuestionId }
            .minByOrNull { it.value.notBefore }
            ?.let { questionFor(it.key) to CardKind.LEARNING }

    private fun pickAnyWorking(allowRepeat: Boolean): Pair<Question, CardKind>? =
        working.entries
            .filter { allowRepeat || it.key != lastQuestionId }
            .minByOrNull { it.value.notBefore }
            ?.let { questionFor(it.key) to CardKind.LEARNING }

    private fun pickDueReview(now: Long): Pair<Question, CardKind>? =
        progressMap.values
            .filter {
                it.questionId in scopeById && it.stage > MemoryModel.LEARNING &&
                    MemoryModel.isDue(it, now) && it.questionId !in working && it.questionId != lastQuestionId
            }
            .minByOrNull { it.dueAtMillis }
            ?.let { questionFor(it.questionId) to CardKind.REVIEW }

    private fun pickNew(): Pair<Question, CardKind>? {
        if (working.size >= config.workingSetSize) return null
        val q = newQueue.removeFirstOrNull() ?: return null
        return q to if (config.previewNew) CardKind.PREVIEW else CardKind.NEW
    }

    private fun pickAhead(now: Long): Pair<Question, CardKind>? {
        if (!config.reviewAhead) return null
        return scopeQuestions
            .filter { it.id != lastQuestionId && it.id in progressMap && it.id !in working }
            .map { it to MemoryModel.recallProbability(progressMap[it.id], now) }
            .let { candidates ->
                val weakest = candidates.minOfOrNull { it.second } ?: return null
                candidates.filter { it.second == weakest }.randomOrNull(random)?.first
            }
            ?.let { it to CardKind.AHEAD }
    }

    private fun Int.saturatingInc() = if (this == Int.MAX_VALUE) this else this + 1

    private companion object {
        /** Cards between a preview and its first recall: long enough to clear short-term echo. */
        const val RECALL_GAP = 3
        const val RELEARN_GAP = 2
        /** At most this many non-new cards in a row while unseen questions remain. */
        const val NEW_CADENCE = 3
    }
}
