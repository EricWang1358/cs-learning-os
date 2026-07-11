package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.QuizSource
import com.cslearningos.mobile.data.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuizEditorStateTest {
    @Test
    fun newQuizEditorClearsExistingQuizIdentityAndFields() {
        val editor = LearningUiState(
            quizEditorId = "quiz-1",
            quizPrompt = "Old prompt",
            quizAnswer = "Old answer",
            quizExplanation = "Old explanation"
        ).forNewQuizEditor()

        assertEquals(AppScreen.QuizEditor, editor.screen)
        assertNull(editor.quizEditorId)
        assertEquals("", editor.quizPrompt)
        assertEquals("", editor.quizAnswer)
        assertEquals("", editor.quizExplanation)
    }

    @Test
    fun existingQuizEditorRetainsIdentityAndPrefillsContent() {
        val quiz = quiz(id = "quiz-2", nodeId = "node-1")

        val editor = LearningUiState().forExistingQuizEditor(quiz)

        assertEquals(AppScreen.QuizEditor, editor.screen)
        assertEquals(quiz, editor.selectedQuiz)
        assertEquals("quiz-2", editor.quizEditorId)
        assertEquals(quiz.prompt, editor.quizPrompt)
        assertEquals(quiz.answer, editor.quizAnswer)
        assertEquals(quiz.explanation, editor.quizExplanation)
    }

    private fun quiz(id: String, nodeId: String) = QuizItemEntity(
        id = id,
        nodeId = nodeId,
        prompt = "Explain a red-black tree.",
        answer = "It is a balanced binary search tree.",
        explanation = "The color invariants bound height.",
        source = QuizSource.manual,
        sourceAnchor = null,
        createdAt = 1L,
        updatedAt = 1L,
        revision = 1L,
        syncStatus = SyncStatus.dirty,
        deletedAt = null
    )
}
