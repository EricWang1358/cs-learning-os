package com.cslearningos.mobile.feature.assistant.data

import com.cslearningos.mobile.feature.assistant.domain.AssistantConversation
import com.cslearningos.mobile.feature.assistant.domain.AssistantConversationCitation
import com.cslearningos.mobile.feature.assistant.domain.AssistantConversationMessage
import com.cslearningos.mobile.feature.assistant.domain.AssistantConversationRole
import com.cslearningos.mobile.feature.assistant.domain.AssistantWorkingDraft
import org.json.JSONArray
import org.json.JSONObject

object AssistantConversationCodec {
    fun encode(conversation: AssistantConversation): String =
        JSONObject()
            .put("id", conversation.id)
            .put(
                "messages",
                JSONArray().apply {
                    conversation.messages.forEach { message ->
                        put(
                            JSONObject()
                                .put("role", message.role.name)
                                .put("body", message.body)
                                .put(
                                    "citations",
                                    JSONArray().apply {
                                        message.citations.forEach { citation ->
                                            put(
                                                JSONObject()
                                                    .put("id", citation.id)
                                                    .put("type", citation.type)
                                                    .put("title", citation.title)
                                                    .put("excerpt", citation.excerpt)
                                            )
                                        }
                                    }
                                )
                        )
                    }
                }
            )
            .apply {
                conversation.workingDraft?.let { draft ->
                    put(
                        "working_draft",
                        JSONObject()
                            .put("title_hint", draft.titleHint)
                            .put("markdown", draft.markdown)
                            .put("area_id", draft.areaId)
                            .put("node_id", draft.nodeId)
                            .put("placement_reason", draft.placementReason)
                    )
                }
            }
            .toString()

    fun decode(raw: String): AssistantConversation {
        val root = JSONObject(raw)
        return AssistantConversation(
            id = root.getString("id"),
            messages = root.getJSONArray("messages").toMessages(),
            workingDraft = root.optJSONObject("working_draft")?.let { draft ->
                AssistantWorkingDraft(
                    titleHint = draft.getString("title_hint"),
                    markdown = draft.getString("markdown"),
                    areaId = draft.optString("area_id").takeIf { it.isNotBlank() },
                    nodeId = draft.optString("node_id").takeIf { it.isNotBlank() },
                    placementReason = draft.optString("placement_reason").takeIf { it.isNotBlank() }
                )
            }
        )
    }

    private fun JSONArray.toMessages(): List<AssistantConversationMessage> =
        buildList {
            for (index in 0 until length()) {
                val message = getJSONObject(index)
                add(
                    AssistantConversationMessage(
                        role = AssistantConversationRole.valueOf(message.getString("role")),
                        body = message.getString("body"),
                        citations = message.optJSONArray("citations")?.toCitations().orEmpty()
                    )
                )
            }
        }

    private fun JSONArray.toCitations(): List<AssistantConversationCitation> =
        buildList {
            for (index in 0 until length()) {
                val citation = getJSONObject(index)
                add(
                    AssistantConversationCitation(
                        id = citation.getString("id"),
                        type = citation.getString("type"),
                        title = citation.getString("title"),
                        excerpt = citation.getString("excerpt")
                    )
                )
            }
        }
}
