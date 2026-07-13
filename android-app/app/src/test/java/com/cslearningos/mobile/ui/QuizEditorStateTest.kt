package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.QuizSource
import com.cslearningos.mobile.data.LearningNodeEntity
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
        assertNull(editor.quizAreaId)
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
        assertNull(editor.quizAreaId)
    }

    @Test
    fun newQuizDraftEditorPrefillsContentAndAreaChoiceStartsEmpty() {
        val editor = LearningUiState(quizAreaId = "systems")
            .forNewQuizDraftEditor(
                prompt = "What is paging?",
                answer = "Paging maps virtual pages to physical frames.",
                explanation = "It enables sparse virtual address spaces."
            )

        assertEquals(AppScreen.QuizEditor, editor.screen)
        assertNull(editor.quizEditorId)
        assertEquals("What is paging?", editor.quizPrompt)
        assertEquals("Paging maps virtual pages to physical frames.", editor.quizAnswer)
        assertEquals("It enables sparse virtual address spaces.", editor.quizExplanation)
        assertNull(editor.quizAreaId)
    }

    @Test
    fun existingQuizEditorUsesQuizNodeWhenANodeWasPreviouslySelected() {
        val nodeA = LearningNodeEntity(
            id = "node-a",
            title = "Node A",
            markdownBody = "# Node A",
            createdAt = 1L,
            updatedAt = 1L,
            lastReadAt = null,
            revision = 1L,
            syncStatus = SyncStatus.clean,
            deletedAt = null
        )
        val quizB = quiz(id = "quiz-b", nodeId = "node-b")

        val editor = LearningUiState(selectedNode = nodeA)
            .forExistingQuizEditorWithoutSelectedNode(quizB)

        assertNull(editor.selectedNode)
        assertEquals("node-b", editor.quizNodeIdForSave())
    }

    @Test
    fun standaloneNewQuizUsesExplicitAreaInsteadOfSelectedNode() {
        val editor = LearningUiState(quizAreaId = "systems")

        assertNull(editor.quizNodeIdForSave())
        assertEquals("systems", editor.quizAreaIdForSave())
    }

    @Test
    fun savingNodeLinkedQuizReturnsToReaderRatherThanReview() {
        val node = LearningNodeEntity(
            id = "node-a",
            title = "Node A",
            markdownBody = "# Node A",
            createdAt = 1L,
            updatedAt = 1L,
            lastReadAt = null,
            revision = 1L,
            syncStatus = SyncStatus.clean,
            deletedAt = null
        )
        val quiz = quiz(id = "quiz-a", nodeId = node.id)

        val state = LearningUiState(selectedNode = node).afterQuizSaved(quiz)

        assertEquals(AppScreen.Reader, state.screen)
        assertEquals(quiz, state.selectedQuiz)
    }

    @Test
    fun savingStandaloneQuizOpensReviewAsItsManagementView() {
        val quiz = quiz(id = "quiz-standalone", nodeId = null)

        val state = LearningUiState().afterQuizSaved(quiz)

        assertEquals(AppScreen.Review, state.screen)
        assertEquals(quiz, state.selectedQuiz)
        assertEquals(quiz.area, state.reviewAreaId)
        assertEquals(false, state.reviewSetupVisible)
    }

    private fun quiz(id: String, nodeId: String?) = QuizItemEntity(
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
