package org.brianwebb.hamsprint.domain

/** One row of a foundation: a fact in its place in the framework, and the questions it answers. */
data class FoundationPoint(val label: String, val value: String, val questionIds: List<String>)

/**
 * A small framework a cluster of questions builds on: a rule with its
 * exceptions, a progression, a table by band. Training teaches it just before
 * the first of its questions, so each new fact lands on structure the learner
 * already has instead of being memorized on its own.
 */
data class Foundation(
    val id: String,
    val title: String,
    val summary: String,
    val points: List<FoundationPoint>,
    /** The pattern or contrast that makes the rows stick, or null. */
    val hook: String?,
    /** In the order they should be introduced, simplest first. */
    val questionIds: List<String>,
    val aid: VisualAid?,
) {
    init {
        require(questionIds.size >= 3) { "$id needs at least 3 questions" }
        require(questionIds.toSet().size == questionIds.size) { "$id lists a question twice" }
        require(points.size in 2..8) { "$id needs 2 to 8 points" }
        require(points.all { p -> p.questionIds.isNotEmpty() && p.questionIds.all { it in questionIds } }) {
            "$id has a point answering a question it does not list"
        }
    }

    /** The pool figures its questions refer to, in question order, so a lesson can show them alongside the rows. */
    fun figures(pool: QuestionPool): List<String> = questionIds.mapNotNull { pool.question(it)?.figure }.distinct()

    /** The rows that answer [questionId]; empty when the question uses the framework as a whole. */
    fun pointsFor(questionId: String): List<FoundationPoint> = points.filter { questionId in it.questionIds }
}

/** One pool's foundations, in the order they are taught. */
class Curriculum(val foundations: List<Foundation>) {
    private val byQuestion: Map<String, Foundation>
    private val rank: Map<String, Int>

    init {
        require(foundations.map { it.id }.toSet().size == foundations.size) { "Foundation ids must be unique" }
        val owners = HashMap<String, Foundation>()
        val ranks = HashMap<String, Int>()
        for (f in foundations) {
            f.questionIds.forEach { q ->
                require(owners.put(q, f) == null) { "$q is in more than one foundation" }
                ranks[q] = ranks.size
            }
        }
        byQuestion = owners
        rank = ranks
    }

    fun foundationOf(questionId: String): Foundation? = byQuestion[questionId]

    /** 1-based position in the curriculum, for "Foundation 3 of 28". */
    fun numberOf(foundation: Foundation): Int = foundations.indexOf(foundation) + 1

    /**
     * Orders questions for introduction: every foundation's questions together,
     * in curriculum order, then the questions no foundation covers, keeping
     * their order in [questions].
     */
    fun order(questions: List<Question>): List<Question> {
        val (framed, rest) = questions.partition { it.id in rank }
        return framed.sortedBy { rank.getValue(it.id) } + rest
    }

    companion object {
        val EMPTY = Curriculum(emptyList())
    }
}
