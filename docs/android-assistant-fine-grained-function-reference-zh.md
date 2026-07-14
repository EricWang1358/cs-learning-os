# Android 知识助理细粒度功能与接口说明

> 代码基线：`codex/android-assistant-area-move` 工作树，2026-07-14。
>
> 注意：本文将“已提交基线”和“当前工作树未提交实现”分开说明。当前未提交改动尚未经过全量测试、状态机接入或设备验证，不应视为完成版本。

## 1. 功能边界

Android 助理是本地优先学习系统中的可选 AI 能力。它可以读取有限的本地上下文、流式接收模型回答、生成可编辑草稿、展示引用和提出受确认保护的动作；它不应自行创建、删除、覆盖或移动本地对象。

本轮新增设计的唯一写操作是“移动一个已有 Node 到一个已有 Area”。模型只提出提案，用户显式点击确认后才会执行。Markdown 正文中的链接不参与该操作，也不会被改写。

## 2. 当前代码分层与入口

| 层 | 主要文件 | 职责 | 上层可调用接口 |
| --- | --- | --- | --- |
| Compose UI | `android-app/app/src/main/java/com/cslearningos/mobile/ui/AssistantScreen.kt` | 显示会话、正文、引用和动作卡 | `LearningViewModel.assistantActions` |
| UI 动作桥接 | `.../feature/assistant/ui/AssistantAppBridge.kt` | 把点击动作调度到协程/协调器，写入应用提示消息 | `sendMessage`、`replyToAgentAction`、`confirmAreaMove` |
| 会话协调器 | `.../feature/assistant/ui/AssistantCoordinator.kt` | 管理输入、流式回复、解析、消息状态和持久化 | `send`、`cancelReply`、`retry`、`confirmAreaMove` |
| 助理领域协议 | `.../feature/assistant/domain/AssistantAgentInteraction.kt` | 解析模型控制指令，不负责数据库写入 | `parseAssistantAgentInteraction`、`toAgentInteraction` |
| 本地上下文/模型会话 | `.../feature/assistant/domain/KnowledgeAssistantSession.kt` | 查询本地资料、Area 列表、调用流式模型服务 | `findLocalContext`、`availableAreas`、`streamReply` |
| 数据门面 | `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningRepository.kt` | 为 UI 提供仓库接口 | `getNode`、`areas`、`moveNodeToArea` |
| Library 实现 | `.../feature/library/data/LibraryRepository.kt` | 更新 Node、索引及关联 Quiz 的 Area 投影 | `moveNodeToArea` |
| 纯状态机模块 | `android-app/domain/assistant/.../AssistantRunMachine.kt` | 按 runId 推导请求阶段与副作用 | `AssistantRunMachine.reduce` |

## 3. 一次普通助理请求的精确路径

1. 用户在 `AssistantScreen` 输入并触发 `AssistantAppBridge.sendMessage()`。
2. `sendMessage()` 调用 `AssistantCoordinator.send(currentSettings())`。
3. `send()` 先读取 `mutableState.value`：输入为空或 `isBusy=true` 时返回 `false`，不会创建请求。
4. `requestModeFor()` 决定 `Answer`、`Draft`、`ReviewQuestion` 或 `ReviewEvaluation`。
5. `queueAssistantTurn()` 立即加入用户消息与占位助理消息，并设置 `isBusy`。
6. `KnowledgeAssistantSession.findLocalContext(input)` 取短本地检索片段；`availableAreas()` 取得模型可引用的 Area 列表。
7. 若 AI 设置不完整，助理消息转为 `ConfigureAi` 动作，不会访问网络。
8. 否则 `session.streamReply(...)` 逐段调用 `onDelta`：普通文本会节流更新消息正文；结构化草稿会隐藏原始 JSON，仅显示“正在思考”。
9. 流结束后，`parseAssistantAgentInteraction(rawReply)` 移除控制块并获得可显示正文和可选动作协议。
10. `completeAssistantTurn()` 写入最终正文、引用、动作、草稿上下文和复习状态，再调用 `persistConversation()`。

### 核心状态字段

`AssistantUiState` 位于 `AssistantUiModels.kt`：

| 字段 | 含义 | 常见问题定位 |
| --- | --- | --- |
| `input` | 当前输入框文本 | 发不出消息时先确认非空 |
| `messages` | 当前会话 UI 消息序列 | 查找动作卡、引用、流式正文 |
| `isBusy` | 是否有运行中的回复 | 一直为 true 会阻止后续发送 |
| `editTarget` | 当前草稿编辑目标及版本 | 草稿/修订被误判时检查此字段 |
| `pendingDraftRequest` | 需要确认的草稿请求 | `sendAgentActionReply()` 会据此强制 Draft 模式 |
| `pendingAutoOpenMessageId` | 完成后需打开编辑器的消息 ID | 自动跳转异常时检查 |

## 4. 控制协议：`cs-agent-action`

模型控制指令必须只出现一次，可使用 HTML 注释块或单个 `json` 代码块：

```text
<!-- cs-agent-action -->
{ ... JSON ... }
<!-- /cs-agent-action -->
```

`parseAssistantAgentInteraction(reply)` 的规则：

- 恰好一个注释控制块：解析其 JSON，正文删除该控制块。
- 否则恰好一个 JSON 代码块：解析并从正文删除该代码块。
- 多个控制块、非法 JSON、未知 `kind`：不产生动作；正文按原样显示。
- 解析失败不会抛到 UI 层，使用 `runCatching(...).getOrNull()` 降级。

### 现有 `kind`

| `kind` | Kotlin 类型 | UI 行为 | 是否直接写数据 |
| --- | --- | --- | --- |
| `confirm` | `AssistantAgentInteraction.Confirm` | 接受、拒绝、自定义文本重新进入模型对话 | 否 |
| `select_context` | `SelectContext` | 多选上下文后发送文本回复 | 否 |
| `move_node_area` | `MoveNodeArea` | 显示“移动到 Area”确认卡 | 仅确认按钮才可能写入 |

### `move_node_area` 严格协议

```json
{
  "kind": "move_node_area",
  "nodeId": "node-1",
  "expectedRevision": 7,
  "targetAreaId": "algorithms",
  "reason": "It belongs with traversal notes."
}
```

`JSONObject.toAgentInteraction()` 对此类型的拒绝条件：

- `nodeId` 为空；
- `targetAreaId` 为空；
- `reason` 为空；
- `expectedRevision` 缺失、不是数值，或小于 `0`。

所有字段满足后才返回 `MoveNodeArea(nodeId, expectedRevision, targetAreaId, reason)`。这层不查询数据库，因此不能仅靠它保证 Area 和 Node 真实存在。

## 5. Area 移动提案：完整调用链

### A. 回复完成时的展示前校验

文件：`AssistantCoordinator.kt`，`send()` 的流结束分支。

```kotlin
agentInteraction.interaction?.takeIf { interaction ->
    interaction !is MoveNodeArea || areas.any { it.id == interaction.targetAreaId }
}
```

- `areas` 来自同一次请求开始时的 `session.availableAreas()`。
- 普通交互直接展示。
- `MoveNodeArea` 只有其 `targetAreaId` 在该快照中存在时才写入 `AssistantMessageAction.AgentInteraction`；未知 Area 不显示确认卡。
- 这是第一道 UX 防线，不是并发安全保证，因为用户点击时 Area 可能已被删除。

### B. UI 确认卡

文件：`AssistantAgentActionCards.kt`。

- `AssistantAgentInteractionCard(...)` 按 sealed type 分派。
- `AssistantMoveNodeAreaCard(...)` 显示目标 `targetAreaId` 和模型给出的 `reason`。
- 仅“接受”按钮调用 `onConfirm(interaction)`；此类卡当前没有“拒绝后重新问模型”的路径。
- `AssistantScreen` 将回调绑定到 `viewModel.assistantActions.confirmAreaMove`。

### C. 桥接层

文件：`AssistantAppBridge.kt`。

```kotlin
fun confirmAreaMove(interaction: MoveNodeArea)
```

- 在 `scope.launch` 内调用协调器的挂起函数。
- 成功：应用总状态 `LearningUiState.message` 写入 `message_node_moved_to_area`。
- 失败：写入 `message_assistant_source_unavailable`。
- 该函数不自行改数据库，避免 Compose/UI 直接持有持久化权限。

### D. 最终校验与提交

文件：`AssistantCoordinator.kt`。

```kotlin
suspend fun confirmAreaMove(interaction: MoveNodeArea): Boolean
```

执行顺序：

1. `repository.getNode(nodeId)`：Node 不存在即失败。
2. 拒绝 `deletedAt != null` 的 Node。
3. 比较 `node.revision == expectedRevision`：不相等说明模型读取后用户已编辑，失败且不写入。
4. `repository.areas.first()` 获取当前 Area 列表，要求 ID 一致且 `deletedAt == null`。
5. 若目标就是 `node.areaId`，返回失败，避免无意义地增加 revision。
6. 调用 `repository.moveNodeToArea(node.id, targetAreaId)`。
7. 在 `messages` 中找到拥有同一 interaction 的动作卡，置 `action = null`，避免重复点击。
8. `persistConversation(conversationId)` 保存去除动作后的会话，再返回 `true`。

### E. 仓库写入语义

最终调用是 `LearningRepository.moveNodeToArea()`，再委派至 `feature/library/data/LibraryRepository.moveNodeToArea()`：

- Node：更新 `updatedAt`、`revision + 1`、`syncStatus = dirty`、`area` slug 和 `areaId`。
- Node FTS：重新索引。
- 该 Node 下的活跃 Quiz：更新 `updatedAt`、`revision + 1`、`syncStatus = dirty` 和 `area` slug，并重新索引。
- **不会读取或改写 `markdownBody`**，因此 Node ID / `linkedNodeId` 引用保持稳定。

### 必须重点检查的风险

1. `LibraryRepository.moveNodeToArea()` 的低层 `resolveArea()` 会为未知 ID 创建隐式 Area；当前协调器已在调用前禁止未知 Area，绕过协调器直接调用仍可能创建 Area。
2. 当前确认卡保存的是对象字段，不含 `messageId` 或一次性 action token；相同提案若出现在多个历史消息中，确认后会清除所有值相等的动作卡。
3. `repository.areas.first()` 依赖 Flow 的第一帧；若 DAO 发出过时缓存，仍需依赖数据库层事务/条件更新才能得到真正的原子并发保护。当前没有条件 `UPDATE ... WHERE revision = ...`。
4. 动作卡标题仍是硬编码英文 `Move note to Area`，尚未加入中英文资源。

## 6. 完成态助理正文的选择/复制

文件：`AssistantMessageBody.kt`（当前未提交新增）。

```kotlin
@Composable
fun AssistantMessageBody(markdown: String) {
    SelectionContainer(modifier = Modifier.testTag("assistant-message-body")) {
        MarkdownRenderer(markdown = markdown, card = false)
    }
}
```

`AssistantScreen.AssistantMessageBubble()` 的渲染分支：

| 消息类型 | 渲染函数 | 是否可选择 |
| --- | --- | --- |
| 完成态 assistant 且正文非空 | `AssistantMessageBody` | 是 |
| 流式 assistant | `Text` | 否 |
| user | `Text` | 否 |
| citations | 各自 `Text.clickable` | 不在选择容器内 |
| action card（确认、草稿、重试） | 独立 Compose 控件 | 不在选择容器内 |

因此长按完成态助理正文应走 Android 原生选择菜单；引用点击与动作按钮不应因选择容器而失效。

当前 Robolectric 测试只能稳定验证选择容器被组成，不能在 JVM 测试中可靠断言 Android 系统文本选择浮层。设备/模拟器必须人工验证：长按正文、拖动选区、复制到剪贴板、点击引用和确认按钮。

## 7. `AssistantRunMachine`：存在但尚未接入

文件：`android-app/domain/assistant/src/main/kotlin/.../AssistantRunMachine.kt`。

它是纯函数状态机：

| 状态 | 允许的下一事件 | 结果/副作用 |
| --- | --- | --- |
| `Idle` | `Start(runId)` | `BuildingContext` + `BuildContext` |
| `BuildingContext` | `ContextReady` | `Streaming` + `StartModel` |
| `Streaming` | `ModelCompleted` | `Parsing` + `ParseResult` |
| `Parsing` | `ParseCompleted` | `Completed` |
| 活跃状态 | `Fail` / `Cancel` / `Supersede` | 相应终态 |

每个非 `Start` 事件都先比较 `event.runId` 与当前状态 runId；不相等时原状态原样返回。这可防止已取消或被新请求替代的异步事件发布结果。

**当前关键事实：** `AssistantCoordinator.send()` 仍通过 `replyJob`、`activeReplyMessageId` 和协程取消管理普通对话，没有把 `Start/ContextReady/ModelCompleted/ParseCompleted` 分派到该机器。因此，`MoveNodeArea` 提案目前也尚未获得机器级 runId guard。这是下一项必须实现并加测试的工作。

## 8. 测试地图与当前覆盖

| 测试文件 | 覆盖内容 | 当前状态 |
| --- | --- | --- |
| `AppLocalizationTest.kt` | 中英文使用指南文案契约 | 已修复并已提交 |
| `AssistantAgentInteractionTest.kt` | 控制块解析、`move_node_area` 字段完整性 | 当前工作树已新增，聚焦测试通过 |
| `AssistantMessageBodyTest.kt` | 完成态正文选择容器被组成 | 当前工作树已新增，聚焦测试通过 |
| `AssistantCoordinatorStateTest.kt` | 协调器 UI 状态 | 尚未为 Area 移动增加有效/未知 Area/陈旧 revision 测试 |
| `AssistantRunMachineTest.kt` | 状态机 runId 规则 | 尚未覆盖新的提案实际接入路径 |

## 9. 建议的排查顺序

1. 在模拟器用真实/模拟模型返回 `move_node_area`，确认控制块从正文消失且只有既有 Area 出现确认卡。
2. 在确认前手工编辑目标 Node，使 revision 改变；点击确认必须失败且 Node Area 不变。
3. 在确认前删除目标 Area；点击确认必须失败且不会创建隐式 Area。
4. 正常确认后检查：Node 的 `areaId/area/revision/syncStatus`、关联 Quiz 的 `area/revision`、FTS 搜索结果和动作卡消失。
5. 长按完成态助理 Markdown 正文测试复制；随后分别点击 citation、草稿/重试/移动确认卡，确认交互未被选择手势吞掉。
6. 快速发送、取消、再发送请求，观察旧流是否还能覆盖新消息；若能复现，优先接入 `AssistantRunMachine`。

## 10. 当前变更清单（未提交）

- `AssistantAgentInteraction.kt`：添加 `MoveNodeArea` 类型、JSON 编解码与字段校验。
- `AssistantCoordinator.kt`：展示前 Area 快照校验、`confirmAreaMove` 最终校验与执行。
- `AssistantAppBridge.kt`：将确认调度到协程并回写应用消息。
- `AssistantAgentActionCards.kt`：移动确认卡。
- `AssistantScreen.kt`：接线确认回调；完成态正文改用 `AssistantMessageBody`。
- `AssistantMessageBody.kt`：仅正文可选择。
- 对应两个聚焦测试：协议解析、正文选择容器。

## 11. 不应在排查中误判为已完成的事项

- 没有实现“模型自动移动”。
- 没有实现批量移动、自动创建 Area、跨设备同步或 Markdown 链接重写。
- 没有给 Area 移动写入做数据库级 compare-and-set 原子条件。
- 没有接入 `AssistantRunMachine` 到真实流式请求。
- 没有完成全量单测、APK 构建、连接设备测试或截图验证。
- 当前工作树有未提交改动；不要在未复核 diff 和运行完整验证前合并到 `main`。
