package com.cslearningos.mobile.feature.assistant.domain

import org.json.JSONArray
import org.json.JSONObject

sealed interface AssistantAgentInteraction {
    data class Confirm(
        val title: String,
        val body: String,
        val acceptReply: String,
        val rejectReply: String,
        val customPlaceholder: String
    ) : AssistantAgentInteraction

    data class SelectContext(
        val title: String,
        val body: String,
        val items: List<SelectContextItem>,
        val confirmReplyPrefix: String
    ) : AssistantAgentInteraction

    data class MoveNodeArea(
        val nodeId: String,
        val expectedRevision: Long,
        val targetAreaId: String,
        val reason: String
    ) : AssistantAgentInteraction
}

data class SelectContextItem(
    val id: String,
    val title: String,
    val body: String,
    val selected: Boolean
)

data class ParsedAssistantAgentInteraction(
    val visibleReply: String,
    val interaction: AssistantAgentInteraction?
)

fun parseAssistantAgentInteraction(reply: String): ParsedAssistantAgentInteraction {
    val commentMatches = AgentActionBlock.findAll(reply).toList()
    if (commentMatches.size == 1) {
        val interaction = runCatching {
            commentMatches.single().groupValues[1].trim().takeIf { it.isNotBlank() }?.let(::JSONObject)?.toAgentInteraction()
        }.getOrNull()
        return ParsedAssistantAgentInteraction(
            visibleReply = reply.replace(AgentActionBlock, "").trim(),
            interaction = interaction
        )
    }

    val codeBlockMatches = CodeBlockJsonAction.findAll(reply).toList()
    if (codeBlockMatches.size == 1) {
        val interaction = runCatching {
            codeBlockMatches.single().groupValues[1].trim().takeIf { it.isNotBlank() }?.let(::JSONObject)?.toAgentInteraction()
        }.getOrNull()
        return ParsedAssistantAgentInteraction(
            visibleReply = reply.replace(CodeBlockJsonAction, "").trim(),
            interaction = interaction
        )
    }

    return ParsedAssistantAgentInteraction(reply.trim(), null)
}

fun AssistantAgentInteraction.toJson(): JSONObject =
    when (this) {
        is AssistantAgentInteraction.Confirm -> JSONObject()
            .put("kind", "confirm")
            .put("title", title)
            .put("body", body)
            .put("acceptReply", acceptReply)
            .put("rejectReply", rejectReply)
            .put("customPlaceholder", customPlaceholder)

        is AssistantAgentInteraction.SelectContext -> JSONObject()
            .put("kind", "select_context")
            .put("title", title)
            .put("body", body)
            .put("confirmReplyPrefix", confirmReplyPrefix)
            .put("items", JSONArray().apply {
                items.forEach { item ->
                    put(
                        JSONObject()
                            .put("id", item.id)
                            .put("title", item.title)
                            .put("body", item.body)
                            .put("selected", item.selected)
                    )
                }
            })

        is AssistantAgentInteraction.MoveNodeArea -> JSONObject()
            .put("kind", "move_node_area")
            .put("nodeId", nodeId)
            .put("expectedRevision", expectedRevision)
            .put("targetAreaId", targetAreaId)
            .put("reason", reason)
    }

fun JSONObject.toAgentInteraction(): AssistantAgentInteraction? =
    when (optString("kind")) {
        "confirm" -> AssistantAgentInteraction.Confirm(
            title = optString("title").takeIf { it.isNotBlank() } ?: "Confirm next step",
            body = optString("body"),
            acceptReply = optString("acceptReply").takeIf { it.isNotBlank() } ?: "Continue.",
            rejectReply = optString("rejectReply").takeIf { it.isNotBlank() } ?: "No, reconsider.",
            customPlaceholder = optString("customPlaceholder").takeIf { it.isNotBlank() } ?: "Type your changes."
        )

        "select_context" -> {
            val items = optJSONArray("items")?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                        val title = item.optString("title").takeIf { it.isNotBlank() } ?: continue
                        add(
                            SelectContextItem(
                                id = id,
                                title = title,
                                body = item.optString("body"),
                                selected = item.optBoolean("selected", false)
                            )
                        )
                    }
                }
            }.orEmpty()
            if (items.isEmpty()) {
                null
            } else {
                AssistantAgentInteraction.SelectContext(
                    title = optString("title").takeIf { it.isNotBlank() } ?: "Select context",
                    body = optString("body"),
                    items = items,
                    confirmReplyPrefix = optString("confirmReplyPrefix").takeIf { it.isNotBlank() } ?: "Please organize these selected items:"
                )
            }
        }

        "move_node_area" -> {
            val nodeId = optString("nodeId").trim()
            val targetAreaId = optString("targetAreaId").trim()
            val reason = optString("reason").trim()
            val expectedRevision = optLong("expectedRevision", -1L)
            if (nodeId.isBlank() || targetAreaId.isBlank() || reason.isBlank() || expectedRevision < 0L) {
                null
            } else {
                AssistantAgentInteraction.MoveNodeArea(nodeId, expectedRevision, targetAreaId, reason)
            }
        }

        else -> null
    }

private val AgentActionBlock = Regex(
    "<!--\\s*cs-agent-action\\s*-->(.*?)<!--\\s*/cs-agent-action\\s*-->",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
)

private val CodeBlockJsonAction = Regex(
    "```\\s*json\\s*(\\{.*?\\})\\s*```",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
)
