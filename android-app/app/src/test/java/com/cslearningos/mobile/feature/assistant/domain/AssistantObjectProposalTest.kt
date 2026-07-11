package com.cslearningos.mobile.feature.assistant.domain

import com.cslearningos.mobile.data.CaptureSlipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantObjectProposalTest {
    @Test
    fun quizProposalRequiresAllEditableFieldsAndKeepsTheOriginalIdentity() {
        val target = AssistantEditTarget.Quiz(
            id = "quiz-1",
            revision = 4L,
            nodeId = "node-1",
            prompt = "Old prompt",
            answer = "Old answer",
            explanation = "Old explanation"
        )

        val proposal = parseAssistantObjectProposal(
            target,
            """
            <!-- cs-quiz-prompt -->What red-black invariant bounds height?<!-- /cs-quiz-prompt -->
            <!-- cs-quiz-answer -->Every root-to-leaf path has the same black height.<!-- /cs-quiz-answer -->
            <!-- cs-quiz-explanation -->That invariant keeps the longest path within twice the shortest.<!-- /cs-quiz-explanation -->
            """.trimIndent(),
            emptyList()
        ) as AssistantEditProposal.Quiz

        assertEquals("quiz-1", proposal.target.id)
        assertEquals("node-1", proposal.target.nodeId)
        assertEquals("What red-black invariant bounds height?", proposal.prompt)
        assertEquals("Every root-to-leaf path has the same black height.", proposal.answer)
    }

    @Test
    fun incompleteQuizProposalDoesNotOverwriteTheCurrentTarget() {
        val target = AssistantEditTarget.Quiz("quiz-1", 4L, null, "Prompt", "Answer", "Explanation")

        val proposal = parseAssistantObjectProposal(
            target,
            "<!-- cs-quiz-prompt -->Replacement prompt<!-- /cs-quiz-prompt -->",
            emptyList()
        )

        assertNull(proposal)
    }

    @Test
    fun quizProposalFailsClosedWhenDirectiveAppearsMoreThanOnce() {
        val target = AssistantEditTarget.Quiz("quiz-1", 4L, null, "Prompt", "Answer", "Explanation")

        val proposal = parseAssistantObjectProposal(
            target,
            """
            <!-- cs-quiz-prompt -->First prompt<!-- /cs-quiz-prompt -->
            <!-- cs-quiz-prompt -->Second prompt<!-- /cs-quiz-prompt -->
            <!-- cs-quiz-answer -->Answer<!-- /cs-quiz-answer -->
            <!-- cs-quiz-explanation -->Explanation<!-- /cs-quiz-explanation -->
            """.trimIndent(),
            emptyList()
        )

        assertNull(proposal)
    }

    @Test
    fun quizProposalFailsClosedWhenReplyContainsResidualPayload() {
        val target = AssistantEditTarget.Quiz("quiz-1", 4L, null, "Prompt", "Answer", "Explanation")

        val proposal = parseAssistantObjectProposal(
            target,
            """
            I changed the quiz:
            <!-- cs-quiz-prompt -->Prompt<!-- /cs-quiz-prompt -->
            <!-- cs-quiz-answer -->Answer<!-- /cs-quiz-answer -->
            <!-- cs-quiz-explanation -->Explanation<!-- /cs-quiz-explanation -->
            """.trimIndent(),
            emptyList()
        )

        assertNull(proposal)
    }

    @Test
    fun captureProposalPreservesTargetAndUsesOnlyKnownSlipTypes() {
        val target = AssistantEditTarget.Capture(
            id = "capture-1",
            revision = 2L,
            body = "Confused about page faults",
            topicHint = "Virtual memory",
            sourceLabel = "Lecture",
            type = CaptureSlipType.unclear
        )

        val proposal = parseAssistantObjectProposal(
            target,
            """
            <!-- cs-capture-body -->Clarify the difference between a TLB miss and a page fault.<!-- /cs-capture-body -->
            <!-- cs-capture-topic -->Virtual memory<!-- /cs-capture-topic -->
            <!-- cs-capture-source -->Lecture 5<!-- /cs-capture-source -->
            <!-- cs-capture-type: question -->
            """.trimIndent(),
            emptyList()
        ) as AssistantEditProposal.Capture

        assertEquals("capture-1", proposal.target.id)
        assertEquals(CaptureSlipType.question, proposal.type)
        assertEquals("Lecture 5", proposal.sourceLabel)
    }

    @Test
    fun captureProposalFailsClosedWhenTypeDirectiveAppearsMoreThanOnce() {
        val target = AssistantEditTarget.Capture(
            id = "capture-1",
            revision = 2L,
            body = "Original",
            topicHint = "Virtual memory",
            sourceLabel = "Lecture",
            type = CaptureSlipType.unclear
        )

        val proposal = parseAssistantObjectProposal(
            target,
            """
            <!-- cs-capture-body -->Body<!-- /cs-capture-body -->
            <!-- cs-capture-topic -->Topic<!-- /cs-capture-topic -->
            <!-- cs-capture-source -->Source<!-- /cs-capture-source -->
            <!-- cs-capture-type: question -->
            <!-- cs-capture-type: unclear -->
            """.trimIndent(),
            emptyList()
        )

        assertNull(proposal)
    }
}
