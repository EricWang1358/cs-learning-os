package com.cslearningos.mobile.assistant.api

enum class AssistantConversationPolicy {
    Fresh,
    Preserve
}

data class AssistantEntryRequest(
    val conversationPolicy: AssistantConversationPolicy,
    val target: AssistantTargetRef? = null
)

data class AssistantTargetRef(
    val type: String,
    val id: String?,
    val revision: Long?
)
