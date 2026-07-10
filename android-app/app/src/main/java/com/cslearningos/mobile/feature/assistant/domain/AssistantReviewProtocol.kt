package com.cslearningos.mobile.feature.assistant.domain

sealed interface AssistantReviewSession {
    data object AwaitingTopic : AssistantReviewSession

    data class AwaitingAnswer(
        val topic: String,
        val question: String
    ) : AssistantReviewSession

    data class Evaluated(
        val topic: String,
        val question: String,
        val quizId: String?
    ) : AssistantReviewSession
}

data class AssistantReviewEvaluation(
    val feedback: String,
    val dailyReviewAnswer: String?
)

fun parseAssistantReviewEvaluation(reply: String): AssistantReviewEvaluation {
    val answer = AssistantReviewAnswerDirective
        .find(reply)
        ?.groupValues
        ?.get(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    return AssistantReviewEvaluation(
        feedback = reply.replace(AssistantReviewAnswerDirective, "").trim(),
        dailyReviewAnswer = answer
    )
}

private val AssistantReviewAnswerDirective = Regex(
    "^\\s*<!--\\s*cs-review-answer:\\s*([^>]+?)\\s*-->\\s*",
    RegexOption.MULTILINE
)
