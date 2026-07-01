package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.CaptureSlipStatus
import com.cslearningos.mobile.data.CaptureSlipType
import com.cslearningos.mobile.data.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderModelsTest {
    @Test
    fun missingFieldsExplainWhyValidationCannotRun() {
        val settings = AiProviderSettings(apiKey = "", baseUrl = "", model = "")

        assertEquals(listOf("API key", "Base URL", "Model"), settings.missingRequiredFields())
    }

    @Test
    fun openAiCompatibleUrlsNormalizeBaseUrl() {
        assertEquals("https://api.deepseek.com/v1/models", aiModelsUrl("https://api.deepseek.com/v1/"))
        assertEquals("https://api.deepseek.com/v1/chat/completions", aiChatCompletionsUrl("https://api.deepseek.com/v1/"))
    }

    @Test
    fun modelPullParsesOpenAiCompatibleList() {
        val raw = """{"data":[{"id":"deepseek-chat"},{"id":"deepseek-reasoner"}]}"""

        assertEquals(listOf("deepseek-chat", "deepseek-reasoner"), parseOpenAiModelIds(raw))
    }

    @Test
    fun chatCompletionExtractsAssistantMarkdown() {
        val raw = """{"choices":[{"message":{"content":"# Virtual Memory\n\n## Core idea"}}]}"""

        assertEquals("# Virtual Memory\n\n## Core idea", parseOpenAiChatContent(raw))
    }

    @Test
    fun aiDraftPromptExplainsTargetOutputForCaptureSlip() {
        val prompt = buildCaptureAiDraftPrompt(
            slip = captureSlip(),
            existingNodes = listOf("Virtual Memory", "TLB Basics")
        )

        assertTrue(prompt.contains("Return Markdown only"))
        assertTrue(prompt.contains("## Explanation"))
        assertTrue(prompt.contains("## Quiz Seeds"))
        assertTrue(prompt.contains("Virtual Memory"))
        assertTrue(prompt.contains("TLB Basics"))
    }

    @Test
    fun aiDraftContextNodeTitlesMatchPromptLimitAndPreflightDisclosure() {
        val titles = (1..14).map { " Node $it " }

        val context = aiDraftContextNodeTitles(titles)

        assertEquals(12, context.size)
        assertEquals("Node 1", context.first())
        assertEquals("Node 12", context.last())
    }

    @Test
    fun markdownTitleFallsBackWhenModelDoesNotReturnHeading() {
        assertEquals("Virtual Memory", titleFromAiMarkdown("# Virtual Memory\n\nBody", fallback = "Capture Draft"))
        assertEquals("Capture Draft", titleFromAiMarkdown("Body only", fallback = "Capture Draft"))
    }

    private fun captureSlip(): CaptureSlipEntity =
        CaptureSlipEntity(
            id = "slip-1",
            body = "I do not understand why a TLB miss triggers a page table walk.",
            type = CaptureSlipType.unclear,
            topicHint = "Virtual Memory",
            sourceLabel = "lecture video",
            linkedNodeId = null,
            status = CaptureSlipStatus.inbox,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            revision = 1,
            syncStatus = SyncStatus.dirty,
            deletedAt = null
        )
}
