package com.cslearningos.mobile.feature.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class AssistantAgentInteractionTest {
    @Test
    fun parsesConfirmationActionAndRemovesDirectiveFromVisibleReply() {
        val parsed = parseAssistantAgentInteraction(
            """
            我建议先生成第一批草稿。

            <!-- cs-agent-action -->
            {
              "kind": "confirm",
              "title": "确认下一步",
              "body": "只生成 1 个节点草稿和 2 张复习题，不直接保存。",
              "acceptReply": "可以，继续生成第一批草稿。",
              "rejectReply": "不好，请重新拆分。",
              "customPlaceholder": "直接输入你的修改意见。"
            }
            <!-- /cs-agent-action -->
            """.trimIndent()
        )

        val action = parsed.interaction as AssistantAgentInteraction.Confirm
        assertEquals("我建议先生成第一批草稿。", parsed.visibleReply)
        assertEquals("确认下一步", action.title)
        assertEquals("可以，继续生成第一批草稿。", action.acceptReply)
        assertEquals("不好，请重新拆分。", action.rejectReply)
        assertEquals("直接输入你的修改意见。", action.customPlaceholder)
    }

    @Test
    fun parsesContextSelectionActionForMixedConversationTopics() {
        val parsed = parseAssistantAgentInteraction(
            """
            上下文里有多个主题，先选择要整理的内容。
            <!-- cs-agent-action -->
            {
              "kind": "select_context",
              "title": "选择要生成节点的内容",
              "body": "秦始皇和马斯克不是同一主题，建议分开处理。",
              "items": [
                {"id": "history", "title": "秦始皇", "body": "统一六国、郡县制、书同文。", "selected": true},
                {"id": "musk", "title": "马斯克", "body": "创业、公司、商业人物。", "selected": false}
              ],
              "confirmReplyPrefix": "请只整理这些内容："
            }
            <!-- /cs-agent-action -->
            """.trimIndent()
        )

        val action = parsed.interaction as AssistantAgentInteraction.SelectContext
        assertEquals("选择要生成节点的内容", action.title)
        assertEquals(2, action.items.size)
        assertTrue(action.items.first().selected)
        assertEquals("请只整理这些内容：", action.confirmReplyPrefix)
    }

    @Test
    fun parsesJsonCodeFenceActionWithoutCrashingOnAndroidRegex() {
        val parsed = parseAssistantAgentInteraction(
            """
            先确认生成范围。
            ```json
            {
              "kind": "confirm",
              "title": "确认下一步",
              "body": "只生成一个节点草稿。",
              "acceptReply": "继续生成。",
              "rejectReply": "先别生成。",
              "customPlaceholder": "输入你的修改意见。"
            }
            ```
            """.trimIndent()
        )

        val action = parsed.interaction as AssistantAgentInteraction.Confirm
        assertEquals("先确认生成范围。", parsed.visibleReply)
        assertEquals("确认下一步", action.title)
        assertEquals("继续生成。", action.acceptReply)
    }

    @Test
    fun ignoresInvalidOrUnknownActionDirectives() {
        assertNull(parseAssistantAgentInteraction("No action").interaction)
        assertNull(
            parseAssistantAgentInteraction(
                """
                Text
                <!-- cs-agent-action -->{"kind":"delete_everything"}<!-- /cs-agent-action -->
                """.trimIndent()
            ).interaction
        )
    }

    @Test
    fun parsesOnlyCompleteNodeAreaMoveProposals() {
        val parsed = parseAssistantAgentInteraction(
            """
            Move the graph note into Algorithms.
            <!-- cs-agent-action -->
            {"kind":"move_node_area","nodeId":"node-1","expectedRevision":7,"targetAreaId":"algorithms","reason":"It belongs with traversal notes."}
            <!-- /cs-agent-action -->
            """.trimIndent()
        )

        val move = parsed.interaction as AssistantAgentInteraction.MoveNodeArea
        assertEquals("Move the graph note into Algorithms.", parsed.visibleReply)
        assertEquals("node-1", move.nodeId)
        assertEquals(7L, move.expectedRevision)
        assertEquals("algorithms", move.targetAreaId)
        assertEquals("It belongs with traversal notes.", move.reason)

        assertNull(
            parseAssistantAgentInteraction(
                """<!-- cs-agent-action -->{"kind":"move_node_area","nodeId":"node-1","targetAreaId":"algorithms","reason":"Missing revision."}<!-- /cs-agent-action -->"""
            ).interaction
        )
    }

    @Test
    fun missingAgentActionFieldsUseReadableFallbackText() {
        val confirm = JSONObject("""{"kind":"confirm"}""").toAgentInteraction() as AssistantAgentInteraction.Confirm
        assertEquals("Confirm next step", confirm.title)
        assertEquals("Continue.", confirm.acceptReply)
        assertEquals("No, reconsider.", confirm.rejectReply)
        assertEquals("Type your changes.", confirm.customPlaceholder)

        val selectContext = JSONObject(
            """
            {
              "kind": "select_context",
              "items": [{"id": "one", "title": "One"}]
            }
            """.trimIndent()
        ).toAgentInteraction() as AssistantAgentInteraction.SelectContext

        assertEquals("Select context", selectContext.title)
        assertEquals("Please organize these selected items:", selectContext.confirmReplyPrefix)
    }
}
