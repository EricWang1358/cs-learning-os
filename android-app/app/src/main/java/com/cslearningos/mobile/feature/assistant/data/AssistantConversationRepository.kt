package com.cslearningos.mobile.feature.assistant.data

import com.cslearningos.mobile.data.LearningDao
import com.cslearningos.mobile.feature.assistant.domain.AssistantConversation

class AssistantConversationRepository(
    private val dao: LearningDao
) {
    suspend fun latest(): AssistantConversation? =
        dao.latestAssistantConversation()?.let { entity ->
            runCatching { AssistantConversationCodec.decode(entity.messagesJson) }.getOrNull()
        }

    suspend fun recent(limit: Int): List<AssistantConversation> =
        dao.recentAssistantConversations(limit).mapNotNull { entity ->
            runCatching { AssistantConversationCodec.decode(entity.messagesJson) }.getOrNull()
        }

    suspend fun get(id: String): AssistantConversation? =
        dao.getAssistantConversation(id)?.let { entity ->
            runCatching { AssistantConversationCodec.decode(entity.messagesJson) }.getOrNull()
        }

    suspend fun delete(id: String) {
        dao.deleteAssistantConversation(id)
    }

    suspend fun save(conversation: AssistantConversation, now: Long = System.currentTimeMillis()) {
        dao.upsertAssistantConversation(
            AssistantConversationEntity(
                id = conversation.id,
                messagesJson = AssistantConversationCodec.encode(conversation),
                updatedAt = now
            )
        )
    }
}
