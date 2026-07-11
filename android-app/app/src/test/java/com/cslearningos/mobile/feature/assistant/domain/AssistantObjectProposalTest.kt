package com.cslearningos.mobile.feature.assistant.domain

import com.cslearningos.mobile.data.CaptureSlipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantObjectProposalTest {
    @Test
    fun nodeProposalAcceptsMarkdownPayloadAndKnownPlacementDirectives() {
        val target = AssistantEditTarget.Node(
            id = "node-1",
            revision = 3L,
            titleHint = "Graph traversal",
            markdown = "# Graph traversal\n\nOld body",
            areaId = "algorithms"
        )

        val proposal = parseAssistantObjectProposal(
            target,
            """
            <!-- cs-area: systems -->
            <!-- cs-area-reason: The revised note focuses on runtime memory behavior. -->
            # Graph traversal

            A `<!-- cs-not-a-directive -->` marker inside prose stays in the Markdown body.

            <!-- cs-capture: Remember to compare BFS queue growth. -->
            """.trimIndent(),
            listOf(
                AssistantAreaOption(id = "algorithms", name = "Algorithms"),
                AssistantAreaOption(id = "systems", name = "Systems")
            )
        ) as AssistantEditProposal.Node

        assertEquals("node-1", proposal.target.id)
        assertEquals("systems", proposal.areaId)
        assertEquals(
            """
            # Graph traversal

            A `<!-- cs-not-a-directive -->` marker inside prose stays in the Markdown body.
            """.trimIndent(),
            proposal.markdown
        )
    }

    @Test
    fun nodeProposalPreservesCaptureSuggestionSeparatelyFromMarkdown() {
        val target = AssistantEditTarget.Node(
            id = null,
            revision = 0L,
            titleHint = "Graph traversal",
            markdown = "",
            areaId = null
        )

        val proposal = parseAssistantObjectProposal(
            target,
            """
            <!-- cs-area: algorithms -->
            <!-- cs-capture: Compare BFS queue growth later. -->
            # Graph traversal

            BFS explores neighbors level by level.
            """.trimIndent(),
            listOf(AssistantAreaOption(id = "algorithms", name = "Algorithms"))
        ) as AssistantEditProposal.Node

        assertEquals("# Graph traversal\n\nBFS explores neighbors level by level.", proposal.markdown)
        assertEquals("Compare BFS queue growth later.", proposal.captureSuggestion)
    }

    @Test
    fun nodeProposalFailsClosedWhenPlacementDirectivesAppearMoreThanOnce() {
        val target = AssistantEditTarget.Node("node-1", 3L, "Graph traversal", "# Graph traversal", "algorithms")

        listOf(
            """
            <!-- cs-area: algorithms -->
            <!-- cs-area: systems -->
            # Graph traversal
            """.trimIndent(),
            """
            <!-- cs-area: algorithms -->
            <!-- cs-area-reason: First reason. -->
            <!-- cs-area-reason: Second reason. -->
            # Graph traversal
            """.trimIndent(),
            """
            <!-- cs-area: algorithms -->
            <!-- cs-capture: First capture. -->
            <!-- cs-capture: Second capture. -->
            # Graph traversal
            """.trimIndent()
        ).forEach { reply ->
            assertNull(
                parseAssistantObjectProposal(
                    target,
                    reply,
                    listOf(AssistantAreaOption(id = "algorithms", name = "Algorithms"))
                )
            )
        }
    }

    @Test
    fun newNodeProposalFailsClosedWhenNoAreaCanBeValidated() {
        val target = AssistantEditTarget.Node(
            id = null,
            revision = 0L,
            titleHint = "First note",
            markdown = "",
            areaId = null
        )

        val proposal = parseAssistantObjectProposal(
            target,
            "# First note\n\nStart here.",
            emptyList()
        )

        assertNull(proposal)
    }

    @Test
    fun nodeProposalFailsClosedForUnknownOrAmbiguousCsDirectives() {
        val target = AssistantEditTarget.Node("node-1", 3L, "Graph traversal", "# Graph traversal", "algorithms")

        listOf(
            "<!-- cs-topic: graphs -->\n# Graph traversal",
            "<!-- cs-area -->\n# Graph traversal",
            "<!-- cs-capture -->\n# Graph traversal"
        ).forEach { reply ->
            assertNull(
                parseAssistantObjectProposal(
                    target,
                    reply,
                    listOf(AssistantAreaOption(id = "algorithms", name = "Algorithms"))
                )
            )
        }
    }

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
