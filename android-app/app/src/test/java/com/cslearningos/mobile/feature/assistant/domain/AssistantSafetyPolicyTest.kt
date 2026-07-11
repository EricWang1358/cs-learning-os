package com.cslearningos.mobile.feature.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantSafetyPolicyTest {
    @Test
    fun localSearchIsReadOnlyButDraftAndCaptureRequireConfirmation() {
        assertFalse(assistantToolPolicy(AssistantTool.LocalSearch).requiresConfirmation)
        assertTrue(assistantToolPolicy(AssistantTool.OpenEditableDraft).requiresConfirmation)
        assertTrue(assistantToolPolicy(AssistantTool.SaveCapture).requiresConfirmation)
    }

    @Test
    fun contextSelectionRemovesBlankEntriesAndStaysWithinThePrivacyBudget() {
        val selected = selectAssistantContext(
            listOf(
                AssistantContextSource(title = "", excerpt = "ignored"),
                AssistantContextSource(title = "Virtual memory", excerpt = "A".repeat(600)),
                AssistantContextSource(title = "TLB", excerpt = "B".repeat(600)),
                AssistantContextSource(title = "Paging", excerpt = "C".repeat(600)),
                AssistantContextSource(title = "Cache", excerpt = "D".repeat(600))
            )
        )

        assertEquals(AssistantSafetyLimits.MaximumContextItems, selected.size)
        assertTrue(selected.all { it.title.isNotBlank() })
        assertTrue(selected.sumOf { it.excerpt.length } <= AssistantSafetyLimits.MaximumContextCharacters)
    }

    @Test
    fun draftIntentIsLimitedToExplicitCreationLanguage() {
        assertEquals(AssistantRequestMode.Draft, assistantRequestModeFor("帮我新建一个虚拟内存笔记"))
        assertEquals(AssistantRequestMode.Draft, assistantRequestModeFor("create a note about cache locality"))
        assertEquals(AssistantRequestMode.Answer, assistantRequestModeFor("为什么 TLB miss 会变慢？"))
    }

    @Test
    fun generalQuestionsUseConfiguredModelKnowledgeWhenLocalSearchIsEmpty() {
        val prompt = buildKnowledgeAssistantSystemPrompt(
            mode = AssistantRequestMode.Answer,
            context = emptyList()
        )

        assertTrue(prompt.contains("general knowledge"))
        assertFalse(prompt.contains("local library is insufficient"))
    }

    @Test
    fun draftPromptRequiresAnExplainableExistingAreaOrOneClarifyingQuestion() {
        val prompt = buildKnowledgeAssistantSystemPrompt(
            mode = AssistantRequestMode.Draft,
            context = emptyList(),
            areas = listOf(AssistantAreaOption(id = "systems", name = "Systems"))
        )

        assertTrue(prompt.contains("cs-area-reason"))
        assertTrue(prompt.contains("one concise clarifying question"))
        assertTrue(prompt.contains("never invent an Area"))
    }
}
