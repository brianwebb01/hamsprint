package org.brianwebb.hamsprint.domain

import java.time.LocalDate

/** The three US amateur license exams, each backed by one NCVEC question pool. */
enum class LicenseClass(val poolId: String, val displayName: String, val element: Int) {
    TECHNICIAN("technician", "Technician", 2),
    GENERAL("general", "General", 3),
    EXTRA("extra", "Amateur Extra", 4);

    companion object {
        fun fromPoolId(id: String): LicenseClass? = entries.firstOrNull { it.poolId == id }
    }
}

data class Question(
    val id: String,
    val groupId: String,
    val text: String,
    val answers: List<String>,
    val correctIndex: Int,
    val refs: String?,
    /** Figure name such as "T-1", or null when the question has no diagram. */
    val figure: String?,
) {
    init {
        require(answers.size == 4) { "$id must have 4 answers" }
        require(correctIndex in answers.indices) { "$id has invalid correct index" }
    }

    val correctAnswer: String get() = answers[correctIndex]
}

/**
 * A topic group such as "G1A". A real exam draws exactly one question from every
 * group, so a question's chance of appearing is 1 / (questions in its group).
 */
data class QuestionGroup(val id: String, val title: String, val questions: List<Question>)

data class Subelement(val id: String, val title: String, val groups: List<QuestionGroup>) {
    val questions: List<Question> get() = groups.flatMap { it.questions }
}

data class QuestionPool(
    val license: LicenseClass,
    val name: String,
    val validFrom: LocalDate,
    val validThrough: LocalDate,
    val revision: String,
    val examQuestions: Int,
    val passingScore: Int,
    val subelements: List<Subelement>,
) {
    val groups: List<QuestionGroup> = subelements.flatMap { it.groups }
    val questions: List<Question> = groups.flatMap { it.questions }

    private val byId = questions.associateBy { it.id }
    private val groupById = groups.associateBy { it.id }

    init {
        require(groups.size == examQuestions) {
            "${license.poolId}: ${groups.size} groups but $examQuestions exam questions"
        }
        require(groups.all { it.questions.isNotEmpty() }) { "${license.poolId}: empty group" }
    }

    fun question(id: String): Question? = byId[id]

    fun groupOf(question: Question): QuestionGroup = groupById.getValue(question.groupId)

    fun subelementOf(question: Question): Subelement =
        subelements.first { sub -> sub.groups.any { it.id == question.groupId } }
}
