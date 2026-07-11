package com.cslearningos.mobile.feature.assistant.ui

import com.cslearningos.mobile.feature.assistant.domain.AssistantRequestMode
import com.cslearningos.mobile.feature.assistant.domain.AssistantAreaOption
import com.cslearningos.mobile.feature.assistant.domain.AssistantEditProposal
import com.cslearningos.mobile.feature.assistant.domain.AssistantEditTarget
import com.cslearningos.mobile.data.CaptureSlipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantActionClaimsTest {
    @Test
    fun typedObjectProposalMapsToItsMatchingConfirmationAction() {
        val proposal = AssistantEditProposal.Capture(
            target = AssistantEditTarget.Capture(
                id = "capture-1",
                revision = 2L,
                body = "Original",
                topicHint = "Paging",
                sourceLabel = "Lecture",
                type = CaptureSlipType.unclear
            ),
            body = "Clarify page faults.",
            topicHint = "Virtual memory",
            sourceLabel = "Lecture 5",
            type = CaptureSlipType.question
        )

        val action = assistantEditAction(proposal) as AssistantMessageAction.OpenEditableCaptureDraft

        assertEquals("capture-1", action.slipId)
        assertEquals(CaptureSlipType.question, action.type)
        assertEquals("Clarify page faults.", action.body)
    }

    @Test
    fun captureSaveActionCanOnlyBeClaimedOnce() {
        val messages = listOf(
            AssistantMessage(
                id = "reply",
                role = AssistantMessageRole.Assistant,
                body = "A useful explanation",
                action = AssistantMessageAction.SaveCapture("A useful explanation")
            )
        )

        val firstClaim = claimCaptureSaveAction(messages, "reply")
        val secondClaim = claimCaptureSaveAction(firstClaim?.messages.orEmpty(), "reply")

        assertNotNull(firstClaim)
        assertEquals("A useful explanation", firstClaim?.action?.body)
        assertNull(secondClaim)
    }

    @Test
    fun retryRequestIsAvailableOnlyForFailedAssistantReply() {
        val messages = listOf(
            AssistantMessage(
                id = "reply",
                role = AssistantMessageRole.Assistant,
                body = "The connection stopped.",
                action = AssistantMessageAction.RetryRequest("Explain virtual memory")
            )
        )

        assertEquals("Explain virtual memory", retryAssistantRequest(messages, "reply"))
        assertNull(retryAssistantRequest(messages, "missing"))
    }

    @Test
    fun genericQuickPromptDoesNotOfferItsClarifyingReplyAsACapture() {
        assertNull(
            assistantReplyAction(
                mode = AssistantRequestMode.Answer,
                request = "解释一个概念",
                reply = "请告诉我想解释的具体概念。",
                areas = emptyList()
            )
        )
    }

    @Test
    fun draftActionUsesOnlyAnExistingAreaDirectiveAndStripsItFromMarkdown() {
        val action = assistantReplyAction(
            mode = AssistantRequestMode.Draft,
            request = "帮我整理成笔记",
            reply = "<!-- cs-area: algorithms -->\n# Divide and conquer",
            areas = listOf(AssistantAreaOption(id = "algorithms", name = "Algorithms"))
        ) as AssistantMessageAction.OpenEditableDraft

        assertEquals("algorithms", action.areaId)
        assertEquals("# Divide and conquer", action.markdown)
    }

    @Test
    fun draftActionExposesTheAssistantsPlacementReasonForUserReview() {
        val action = assistantReplyAction(
            mode = AssistantRequestMode.Draft,
            request = "Create a note about recursion",
            reply = "<!-- cs-area: algorithms -->\n<!-- cs-area-reason: Recursion matches the existing algorithm problem-solving notes. -->\n# Recursion",
            areas = listOf(AssistantAreaOption(id = "algorithms", name = "Algorithms"))
        ) as AssistantMessageAction.OpenEditableDraft

        assertEquals("algorithms", action.areaId)
        assertEquals("Recursion matches the existing algorithm problem-solving notes.", action.placementReason)
    }

    @Test
    fun ambiguousDraftReplyBecomesAClarifyingQuestionInsteadOfAnUntitledNode() {
        val decision = assistantReplyDecision(
            mode = AssistantRequestMode.Draft,
            request = "Create a note about an interview topic",
            reply = "Which Area should this interview topic belong to: Algorithms, Systems, or Projects?",
            areas = listOf(AssistantAreaOption(id = "algorithms", name = "Algorithms"))
        )

        assertNull(decision.action)
        assertNull(decision.editTarget)
        assertEquals("Which Area should this interview topic belong to: Algorithms, Systems, or Projects?", decision.visibleReply)
    }

    @Test
    fun draftRevisionReplacesTheWorkingDraftAndSeparatesCaptureOnlyContent() {
        val decision = assistantReplyDecision(
            mode = AssistantRequestMode.Draft,
            request = "补一段每周计划",
            reply = "<!-- cs-capture: 找朋友一起刷题 -->\n<!-- cs-area: algorithms -->\n# LeetCode plan\n\n## Weekly plan",
            areas = listOf(AssistantAreaOption(id = "algorithms", name = "Algorithms")),
            editTarget = AssistantEditTarget.Node(
                id = "node-42",
                revision = 3L,
                titleHint = "LeetCode plan",
                markdown = "# LeetCode plan",
                areaId = "algorithms"
            )
        )

        assertEquals("# LeetCode plan\n\n## Weekly plan", decision.editTarget?.markdown)
        assertEquals("algorithms", decision.editTarget?.areaId)
        assertEquals("node-42", decision.editTarget?.id)
        assertEquals("找朋友一起刷题", decision.captureSuggestion)
    }

    @Test
    fun revisionOfAnExistingNodeCanProposeAnotherExistingAreaForConfirmedSave() {
        val decision = assistantReplyDecision(
            mode = AssistantRequestMode.Draft,
            request = "Refine this node",
            reply = "<!-- cs-area: systems -->\n# Graph traversal",
            areas = listOf(
                AssistantAreaOption(id = "algorithms", name = "Algorithms"),
                AssistantAreaOption(id = "systems", name = "Systems")
            ),
            editTarget = AssistantEditTarget.Node(
                id = "node-42",
                revision = 3L,
                titleHint = "Graph traversal",
                markdown = "# Graph traversal",
                areaId = "algorithms"
            )
        )

        assertEquals("systems", decision.editTarget?.areaId)
        assertEquals("node-42", decision.editTarget?.id)
    }
}
