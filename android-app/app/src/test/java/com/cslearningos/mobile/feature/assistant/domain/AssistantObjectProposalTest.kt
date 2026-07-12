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
    fun nodeProposalNormalizesLooseAreaCommentAndDoesNotLeakItIntoMarkdown() {
        val target = AssistantEditTarget.Node(
            id = null,
            revision = 0L,
            titleHint = "Prompt structure",
            markdown = "",
            areaId = null
        )

        val proposal = parseAssistantObjectProposal(
            target,
            """
            <!-- area: abilities -->
            # Prompt structure

            ##Task description

            text#Output requirements

            Keep every important source detail.
            """.trimIndent(),
            listOf(AssistantAreaOption(id = "abilities", name = "Abilities"))
        ) as AssistantEditProposal.Node

        assertEquals("abilities", proposal.areaId)
        assertEquals(
            """
            # Prompt structure

            ## Task description

            # Output requirements

            Keep every important source detail.
            """.trimIndent(),
            proposal.markdown
        )
    }

    @Test
    fun nodeProposalExtractsMarkdownFenceWhenAssistantWrapsDraftInExplanatoryText() {
        val target = AssistantEditTarget.Node(
            id = "node-1",
            revision = 3L,
            titleHint = "我的第一个 Kotlin 程序",
            markdown = "# 我的第一个 Kotlin 程序\n\n旧内容",
            areaId = "abilities"
        )

        val proposal = parseAssistantObjectProposal(
            target,
            """
            我已对节点进行了整理和优化：

            - 保留了原有有用内容。
            - 修正了 Markdown 结构。

            下面是完整的修订版 Markdown，你可以直接保存：

            ```markdown
            # 我的第一个 Kotlin 程序

            ## 核心概念
            - Kotlin 源文件通常以 `.kt` 结尾。
            ```
            """.trimIndent(),
            listOf(AssistantAreaOption(id = "abilities", name = "Abilities"))
        ) as AssistantEditProposal.Node

        assertEquals(
            """
            # 我的第一个 Kotlin 程序

            ## 核心概念
            - Kotlin 源文件通常以 `.kt` 结尾。
            """.trimIndent(),
            proposal.markdown
        )
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
    fun newNodeProposalCreatesEditableDraftFromPlainMarkdownWhenAreaExists() {
        val target = AssistantEditTarget.Node(
            id = null,
            revision = 0L,
            titleHint = "创建笔记：秦始皇的历史功绩",
            markdown = "",
            areaId = null
        )

        val proposal = parseAssistantObjectProposal(
            target,
            """
            Area: history
            # 秦始皇的历史功绩

            秦始皇统一六国，建立中央集权制度，并推动书同文、车同轨。
            """.trimIndent(),
            listOf(
                AssistantAreaOption(id = "history", name = "History"),
                AssistantAreaOption(id = "systems", name = "Systems")
            )
        ) as AssistantEditProposal.Node

        assertEquals("history", proposal.areaId)
        assertEquals(
            """
            # 秦始皇的历史功绩

            秦始皇统一六国，建立中央集权制度，并推动书同文、车同轨。
            """.trimIndent(),
            proposal.markdown
        )
    }

    @Test
    fun newNodeProposalRequiresMarkdownHeadingBeforeEditableDraft() {
        val target = AssistantEditTarget.Node(
            id = null,
            revision = 0L,
            titleHint = "Create note",
            markdown = "",
            areaId = null
        )

        val proposal = parseAssistantObjectProposal(
            target,
            """
            Area: abilities
            I generated a complete draft for you.

            This covers setup, conversation management, and prompts.
            """.trimIndent(),
            listOf(AssistantAreaOption(id = "abilities", name = "Abilities"))
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
