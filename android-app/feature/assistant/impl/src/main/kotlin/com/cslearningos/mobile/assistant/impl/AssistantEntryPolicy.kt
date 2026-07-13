package com.cslearningos.mobile.assistant.impl

import com.cslearningos.mobile.assistant.api.AssistantConversationPolicy
import com.cslearningos.mobile.assistant.api.AssistantEntryRequest

object AssistantEntryPolicy {
    fun shouldReset(request: AssistantEntryRequest): Boolean =
        request.conversationPolicy == AssistantConversationPolicy.Fresh
}
