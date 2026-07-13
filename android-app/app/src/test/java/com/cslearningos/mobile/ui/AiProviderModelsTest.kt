package com.cslearningos.mobile.ui

import com.cslearningos.mobile.R
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

        assertEquals(
            listOf(R.string.more_api_key_label, R.string.more_base_url_label, R.string.more_model_label),
            settings.missingRequiredFields()
        )
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
        assertTrue(prompt.contains("## Review Cards"))
        assertTrue(prompt.contains(":::quiz"))
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

    @Test
    fun markdownTitleIgnoresCollapsedSecondHeadingInSameLine() {
        assertEquals(
            "我的第一个 Kotlin程序",
            titleFromAiMarkdown("# 我的第一个 Kotlin程序##核心概念-Kotlin源文件", fallback = "Capture Draft")
        )
    }

    @Test
    fun assistantComposerSummarizesLongInputAsTextAttachment() {
        val attachment = assistantComposerTextAttachment("a".repeat(900))

        assertEquals("material.txt", attachment?.fileName)
        assertEquals(900, attachment?.characterCount)
        assertTrue(attachment?.preview?.length ?: 0 <= 96)
    }

    @Test
    fun assistantMarkdownDraftSplitsFirstTitleHeadingOutOfBody() {
        val draft = assistantMarkdownDraft(
            """
            # My first Kotlin program

            ## Core concepts
            - Kotlin files often end with `.kt`.
            """.trimIndent(),
            fallback = "Capture Draft"
        )

        assertEquals("My first Kotlin program", draft.title)
        assertEquals(
            """
            ## Core concepts
            - Kotlin files often end with `.kt`.
            """.trimIndent(),
            draft.body
        )
    }

    @Test
    fun assistantMarkdownDraftUsesPlainTitleDirectiveBeforeMarkdownHeading() {
        val draft = assistantMarkdownDraft(
            """
            <!-- cs-title: Skill 触发机制 -->
            # Model accidentally returned a Markdown title too

            ## Core concepts
            Skills are chosen before the skill body is loaded.
            """.trimIndent(),
            fallback = "Capture Draft"
        )

        assertEquals("Skill 触发机制", draft.title)
        assertEquals(
            """
            ## Core concepts
            Skills are chosen before the skill body is loaded.
            """.trimIndent(),
            draft.body
        )
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
