package com.cslearningos.mobile.feature.assistant.domain

data class AssistantConversation(
    val id: String,
    val messages: List<AssistantConversationMessage>,
    val editTarget: AssistantEditTarget? = null
)

data class AssistantConversationMessage(
    val role: AssistantConversationRole,
    val body: String,
    val citations: List<AssistantConversationCitation> = emptyList(),
    val action: AssistantConversationAction? = null
)

sealed interface AssistantConversationAction {
    data class AgentInteraction(val interaction: AssistantAgentInteraction) : AssistantConversationAction

    data class OpenEditableNodeDraft(
        val nodeId: String?,
        val expectedRevision: Long,
        val titleHint: String,
        val markdown: String,
        val areaId: String?,
        val placementReason: String? = null
    ) : AssistantConversationAction

    data class OpenEditableQuizDraft(
        val quizId: String,
        val expectedRevision: Long,
        val nodeId: String?,
        val prompt: String,
        val answer: String,
        val explanation: String
    ) : AssistantConversationAction

    data class OpenEditableCaptureDraft(
        val slipId: String,
        val expectedRevision: Long,
        val body: String,
        val topicHint: String,
        val sourceLabel: String,
        val typeName: String
    ) : AssistantConversationAction

    data class SaveCapture(val body: String) : AssistantConversationAction
    data class RetryRequest(val prompt: String) : AssistantConversationAction
    data object OpenDailyReview : AssistantConversationAction
    data object ConfigureAi : AssistantConversationAction
}

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
