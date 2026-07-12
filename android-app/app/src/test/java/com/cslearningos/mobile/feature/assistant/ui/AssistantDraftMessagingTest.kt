package com.cslearningos.mobile.feature.assistant.ui

import com.cslearningos.mobile.R
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantDraftMessagingTest {
    @Test
    fun draftReadyMessageRequestsAreaWhenReviewCardsExistButAreaIsMissing() {
        val messageRes = assistantDraftReadyMessageResId(
            markdown = """
                # Paging

                ## Review cards

                :::quiz
                question: What does a page table map?
                answer: Virtual pages to physical frames.
                explanation: The MMU uses this mapping during translation.
                :::
            """.trimIndent(),
            areaId = null
        )

        assertEquals(R.string.message_assistant_draft_choose_area_for_review, messageRes)
    }

    @Test
    fun draftReadyMessageStaysGenericWhenAreaAlreadyExists() {
        val messageRes = assistantDraftReadyMessageResId(
            markdown = """
                # Paging

                :::quiz
                question: What does a page table map?
                answer: Virtual pages to physical frames.
                explanation: The MMU uses this mapping during translation.
                :::
            """.trimIndent(),
            areaId = "systems"
        )

        assertEquals(R.string.message_assistant_draft_ready, messageRes)
    }
}
