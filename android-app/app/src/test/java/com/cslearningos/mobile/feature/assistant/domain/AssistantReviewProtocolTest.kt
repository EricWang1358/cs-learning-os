package com.cslearningos.mobile.feature.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantReviewProtocolTest {
    @Test
    fun evaluationSeparatesFeedbackFromDailyReviewAnswerDirective() {
        val evaluation = parseAssistantReviewEvaluation(
            "You covered the invariant, but did not explain the base case.\n<!-- cs-review-answer: The invariant holds before and after every loop iteration. -->"
        )

        assertEquals("You covered the invariant, but did not explain the base case.", evaluation.feedback)
        assertEquals("The invariant holds before and after every loop iteration.", evaluation.dailyReviewAnswer)
    }

    @Test
    fun reviewEvaluationPromptExplainsTheDailyReviewToolCall() {
        val prompt = buildKnowledgeAssistantSystemPrompt(
            mode = AssistantRequestMode.ReviewEvaluation,
            context = emptyList()
        )

        assertTrue(prompt.contains("cs-review-answer"))
        assertTrue(prompt.contains("interviewer"))
        assertTrue(prompt.contains("editable review-card draft"))
        assertTrue(prompt.contains("must choose an Area and save"))
    }
}
