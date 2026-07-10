package com.cslearningos.mobile.feature.assistant.ui

import com.cslearningos.mobile.feature.assistant.domain.AssistantRequestMode
import com.cslearningos.mobile.feature.assistant.domain.AssistantAreaOption
import com.cslearningos.mobile.feature.assistant.domain.AssistantWorkingDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantActionClaimsTest {
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
    fun draftRevisionReplacesTheWorkingDraftAndSeparatesCaptureOnlyContent() {
        val decision = assistantReplyDecision(
            mode = AssistantRequestMode.Draft,
            request = "补一段每周计划",
            reply = "<!-- cs-capture: 找朋友一起刷题 -->\n<!-- cs-area: algorithms -->\n# LeetCode plan\n\n## Weekly plan",
            areas = listOf(AssistantAreaOption(id = "algorithms", name = "Algorithms")),
            workingDraft = AssistantWorkingDraft(
                titleHint = "LeetCode plan",
                markdown = "# LeetCode plan",
                areaId = "algorithms",
                nodeId = "node-42"
            )
        )

        assertEquals("# LeetCode plan\n\n## Weekly plan", decision.workingDraft?.markdown)
        assertEquals("algorithms", decision.workingDraft?.areaId)
        assertEquals("node-42", decision.workingDraft?.nodeId)
        assertEquals("找朋友一起刷题", decision.captureSuggestion)
    }
}
