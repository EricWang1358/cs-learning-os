package com.cslearningos.mobile.feature.assistant.domain

data class AssistantConversation(
    val id: String,
    val messages: List<AssistantConversationMessage>,
    val workingDraft: AssistantWorkingDraft? = null
)

data class AssistantWorkingDraft(
    val titleHint: String,
    val markdown: String,
    val areaId: String?,
    val nodeId: String? = null
)

data class AssistantConversationMessage(
    val role: AssistantConversationRole,
    val body: String,
    val citations: List<AssistantConversationCitation> = emptyList()
)

data class AssistantConversationCitation(
    val id: String,
    val type: String,
    val title: String,
    val excerpt: String
)

enum class AssistantConversationRole {
    User,
    Assistant
}
