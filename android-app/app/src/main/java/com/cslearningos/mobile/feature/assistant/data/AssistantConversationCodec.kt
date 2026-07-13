package com.cslearningos.mobile.feature.assistant.data

import com.cslearningos.mobile.feature.assistant.domain.AssistantConversation
import com.cslearningos.mobile.feature.assistant.domain.AssistantConversationAction
import com.cslearningos.mobile.feature.assistant.domain.AssistantConversationCitation
import com.cslearningos.mobile.feature.assistant.domain.AssistantConversationMessage
import com.cslearningos.mobile.feature.assistant.domain.AssistantConversationRole
import com.cslearningos.mobile.feature.assistant.domain.AssistantEditTarget
import com.cslearningos.mobile.feature.assistant.domain.toAgentInteraction
import com.cslearningos.mobile.feature.assistant.domain.toJson
import com.cslearningos.mobile.data.CaptureSlipType
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
                                .apply { message.action?.let { put("action", it.toJson()) } }
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
                conversation.editTarget?.let { target ->
                    put("edit_target", target.toJson())
                }
            }
            .toString()

    fun decode(raw: String): AssistantConversation {
        val root = JSONObject(raw)
        return AssistantConversation(
            id = root.getString("id"),
            messages = root.getJSONArray("messages").toMessages(),
            editTarget = root.optJSONObject("edit_target")?.toObjectTarget()
                ?: root.optJSONObject("object_target")?.toObjectTarget()
                ?: root.optJSONObject("working_draft")?.let { draft ->
                    AssistantEditTarget.Node(
                        id = draft.optString("node_id").takeIf { it.isNotBlank() },
                        revision = 0L,
                        titleHint = draft.getString("title_hint"),
                        markdown = draft.getString("markdown"),
                        areaId = draft.optString("area_id").takeIf { it.isNotBlank() }
                    )
                }
        )
    }

    private fun AssistantEditTarget.toJson(): JSONObject = JSONObject()
        .put("revision", revision)
        .apply {
            id?.let { put("id", it) }
            when (this@toJson) {
                is AssistantEditTarget.Node -> put("kind", "node").put("title_hint", titleHint).put("markdown", markdown).put("area_id", areaId)
                is AssistantEditTarget.Quiz -> put("kind", "quiz").put("node_id", nodeId).put("prompt", prompt).put("answer", answer).put("explanation", explanation)
                is AssistantEditTarget.Capture -> put("kind", "capture").put("body", body).put("topic_hint", topicHint).put("source_label", sourceLabel).put("type", type.name)
            }
        }

    private fun JSONObject.toObjectTarget(): AssistantEditTarget? {
        return when (optString("kind")) {
            "node" -> AssistantEditTarget.Node(
                id = optString("id").takeIf { it.isNotBlank() },
                revision = optLong("revision", 0L).coerceAtLeast(0L),
                titleHint = optString("title_hint"),
                markdown = optString("markdown"),
                areaId = optString("area_id").takeIf { it.isNotBlank() }
            )

            "quiz" -> {
                val id = optString("id").takeIf { it.isNotBlank() } ?: return null
                val revision = optLong("revision", -1L).takeIf { it >= 0 } ?: return null
                AssistantEditTarget.Quiz(id, revision, optString("node_id").takeIf { it.isNotBlank() }, optString("prompt"), optString("answer"), optString("explanation"))
            }

            "capture" -> CaptureSlipType.entries.firstOrNull { it.name == optString("type") }?.let { type ->
                val id = optString("id").takeIf { it.isNotBlank() } ?: return null
                val revision = optLong("revision", -1L).takeIf { it >= 0 } ?: return null
                AssistantEditTarget.Capture(id, revision, optString("body"), optString("topic_hint"), optString("source_label"), type)
            }
            else -> null
        }
    }

    private fun AssistantConversationAction.toJson(): JSONObject = JSONObject().apply {
        when (this@toJson) {
            is AssistantConversationAction.AgentInteraction -> put("kind", "agent_interaction")
                .put("interaction", interaction.toJson())

            is AssistantConversationAction.OpenEditableNodeDraft -> put("kind", "open_node_draft")
                .put("node_id", nodeId ?: JSONObject.NULL)
                .put("expected_revision", expectedRevision)
                .put("title_hint", titleHint)
                .put("markdown", markdown)
                .put("area_id", areaId)
                .put("placement_reason", placementReason)

            is AssistantConversationAction.OpenEditableQuizDraft -> put("kind", "open_quiz_draft")
                .put("quiz_id", quizId)
                .put("expected_revision", expectedRevision)
                .put("node_id", nodeId)
                .put("prompt", prompt)
                .put("answer", answer)
                .put("explanation", explanation)

            is AssistantConversationAction.OpenNewQuizDraft -> put("kind", "open_new_quiz_draft")
                .put("prompt", prompt)
                .put("answer", answer)
                .put("explanation", explanation)

            is AssistantConversationAction.OpenEditableCaptureDraft -> put("kind", "open_capture_draft")
                .put("slip_id", slipId)
                .put("expected_revision", expectedRevision)
                .put("body", body)
                .put("topic_hint", topicHint)
                .put("source_label", sourceLabel)
                .put("type", typeName)

            is AssistantConversationAction.SaveCapture -> put("kind", "save_capture").put("body", body)
            is AssistantConversationAction.RetryRequest -> put("kind", "retry_request").put("prompt", prompt)
            AssistantConversationAction.OpenDailyReview -> put("kind", "open_daily_review")
            AssistantConversationAction.ConfigureAi -> put("kind", "configure_ai")
        }
    }

    private fun JSONObject.toAction(): AssistantConversationAction? =
        when (optString("kind")) {
            "agent_interaction" -> optJSONObject("interaction")
                ?.toAgentInteraction()
                ?.let(AssistantConversationAction::AgentInteraction)

            "open_node_draft" -> {
                val nodeId = requiredNullableNonBlankString("node_id") ?: return null
                AssistantConversationAction.OpenEditableNodeDraft(
                    nodeId = nodeId.value,
                    expectedRevision = optLong("expected_revision", -1L),
                    titleHint = optString("title_hint"),
                    markdown = optString("markdown"),
                    areaId = optString("area_id").takeIf { it.isNotBlank() },
                    placementReason = optString("placement_reason").takeIf { it.isNotBlank() }
                ).takeIf { it.expectedRevision >= 0 }
            }

            "open_quiz_draft" -> AssistantConversationAction.OpenEditableQuizDraft(
                quizId = optString("quiz_id"),
                expectedRevision = optLong("expected_revision", -1L),
                nodeId = optString("node_id").takeIf { it.isNotBlank() },
                prompt = optString("prompt"),
                answer = optString("answer"),
                explanation = optString("explanation")
            ).takeIf { it.quizId.isNotBlank() && it.expectedRevision >= 0 }

            "open_new_quiz_draft" -> AssistantConversationAction.OpenNewQuizDraft(
                prompt = optString("prompt"),
                answer = optString("answer"),
                explanation = optString("explanation")
            ).takeIf { it.prompt.isNotBlank() && it.answer.isNotBlank() }

            "open_capture_draft" -> AssistantConversationAction.OpenEditableCaptureDraft(
                slipId = optString("slip_id"),
                expectedRevision = optLong("expected_revision", -1L),
                body = optString("body"),
                topicHint = optString("topic_hint"),
                sourceLabel = optString("source_label"),
                typeName = optString("type")
            ).takeIf { it.slipId.isNotBlank() && it.expectedRevision >= 0 }

            "save_capture" -> AssistantConversationAction.SaveCapture(optString("body")).takeIf { it.body.isNotBlank() }
            "retry_request" -> AssistantConversationAction.RetryRequest(optString("prompt")).takeIf { it.prompt.isNotBlank() }
            "open_daily_review" -> AssistantConversationAction.OpenDailyReview
            "configure_ai" -> AssistantConversationAction.ConfigureAi
            else -> null
        }

    private data class NullableStringField(val value: String?)

    private fun JSONObject.requiredNullableNonBlankString(name: String): NullableStringField? {
        if (!has(name)) return null
        if (isNull(name)) return NullableStringField(null)
        val value = opt(name) as? String ?: return null
        return value.takeIf { it.isNotBlank() }?.let(::NullableStringField)
    }

    private fun JSONArray.toMessages(): List<AssistantConversationMessage> =
        buildList {
            for (index in 0 until length()) {
                val message = getJSONObject(index)
                add(
                    AssistantConversationMessage(
                        role = AssistantConversationRole.valueOf(message.getString("role")),
                        body = message.getString("body"),
                        action = message.optJSONObject("action")?.toAction(),
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
