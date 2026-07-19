# React Client 与 Android 对齐计划

## 范围与状态约定

本计划的客户端范围仅为 `app/`：它是由本机 FastAPI 提供数据的 React Web 客户端。目标是复用已有 API，使 Web 的工作流与 Android 的已实现能力和安全契约对齐；不复制 Android 的 Compose 页面、导航或视觉布局，也不新建 Electron、账号或云端产品。

“已实现”只表示当前源码已有实现；“计划”是后续客户端工作；“延期”不是当前能力。Android 的本地存储以 Room 为准，Web 的业务数据以 FastAPI 数据库和内容目录为准；`localStorage` 目前只保存 Web 的未保存编辑草稿和 Daily Bite 进度，不能表述为离线业务数据库。

## 能力矩阵

| 能力 | Android 当前状态 | React Web 当前状态（仅源码已支持） | FastAPI 依赖 | 下一步客户端动作 | 验收证据 |
| --- | --- | --- | --- | --- | --- |
| 本地数据 | 已实现：`LearningRepository` 读取和写入本地 Room 数据；核心学习流不依赖后端。 | 已实现：`App.tsx` 经 `/api/nodes`、`/api/quizzes` 等读取和写入本地 FastAPI 服务；编辑草稿另存于 `localStorage`。没有浏览器端完整本地库或离线队列。 | `/api/nodes`、`/api/quizzes`、`/api/review/due`。 | 在 Web 显示“数据由本机 API 管理”的状态和失败态；不要把 `localStorage` 扩展为权威数据源。 | 断开后端时 UI 明确失败且不伪称离线；恢复后重新加载的数据与 API 返回一致。源码：`android-app/app/src/main/java/com/cslearningos/mobile/data/LearningRepository.kt`、`app/src/lib/editDraftStorage.ts`、`app/src/App.tsx`。 |
| 节点与 Area | 已实现：本地节点编辑、Area 归类和库浏览。 | 已实现：列出 Area、按 Area/track 浏览，创建节点及编辑节点正文；当前没有 Web 创建、重命名或删除 Area 的路由调用。 | `/api/areas`、`/api/areas/{area}/tracks`、`/api/nodes`、`/api/nodes/{slug}/body`。 | 以现有 Area 作为节点编辑器的受控选项；先定义 Area 写入契约，再增加 Area 管理 UI，不能假设路由存在。 | 新建和编辑节点后重新请求可在同一 Area/track 找到；未实现的 Area 写操作无入口。源码：`app/src/App.tsx`、`backend/node_router.py`。 |
| Capture | 已实现：Capture Slip 可创建、归档、恢复、删除，并可整理为节点或 AI 草稿。 | 未实现：没有 Capture Slip 列表、创建或提升流程。 | 现有后端未暴露 Capture 路由。 | 先定义并实现有界 Capture DTO/路由及测试，再增加独立 Capture 工作区；不得将“新建节点”称为 Capture。 | 一条 slip 可新建、编辑、归档、恢复、提升为待保存节点草稿；无后端时不出现虚假的成功状态。Android 证据：`android-app/app/src/main/java/com/cslearningos/mobile/feature/capture/`、`android-app/app/src/test/java/com/cslearningos/mobile/ui/CaptureWorkflowModelsTest.kt`。 |
| 复习 | 已实现：本地到期队列、显示答案和 Again/Hard/Good 评分。 | 已实现：读取到期复习、读取 quiz，并提交尝试结果。 | `/api/review/due`、`/api/quizzes/{quiz_id}`、`/api/quizzes/{quiz_id}/attempts`。 | 对齐到期为空、提交失败重试和评分后刷新队列；按 Android 语义保留 Again 的同轮复习，不修改调度规则。 | 提交一次评分后仅对应卡片和队列状态更新；Again 不在当前会话中消失。源码：`app/src/App.tsx`、`backend/quiz_router.py`、`android-app/app/src/test/java/com/cslearningos/mobile/ui/ReviewQueueModelsTest.kt`。 |
| 回收站 | 已实现：节点回收站可恢复或永久删除；活动学习流排除已删除数据。 | 已实现：节点移入 Trashbin、恢复、永久删除，并在侧栏显示 trash。 | `/api/nodes/{slug}/trash`、`/api/nodes/{slug}/restore`、`DELETE /api/nodes/{slug}`。 | 为 Web 操作补充端到端测试：移入、刷新、恢复、永久删除；删除前继续明确确认。 | 移入后普通列表和复习不显示，Trashbin 可恢复；永久删除后 GET 不再返回节点。源码：`app/src/App.tsx`、`backend/node_router.py`、`android-app/app/src/main/java/com/cslearningos/mobile/feature/library/domain/RestoreNodeFromTrashUseCase.kt`。 |
| 备份与恢复 | 已实现：JSON 导出和显式恢复；导入有记录数、字段长度、嵌套等上限，恢复在仓储层执行。 | 部分实现：可调用 `/api/package/export?write=true` 请求服务端写出导出包；没有导入、预览或恢复 UI。当前导出写操作错误地使用 GET。 | 当前仅 `/api/package/export?write=true`；恢复 API 尚不存在。 | 先把写出导出改为显式 POST，再设计有大小/结构限制的上传预检、预览、确认恢复和失败原子性；后端契约完成前不显示“恢复”。 | 超限/损坏文件不改变数据；预览确认后才恢复；导出路由不再有副作用 GET。源码：`app/src/App.tsx`、`backend/productization_router.py`、`android-app/app/src/main/java/com/cslearningos/mobile/data/BackupCodec.kt`。 |
| Markdown 表格与安全 | 已实现：本地解析 Markdown 和 GFM 表格；链接通过 `isSafeMarkdownDestination` 限制。 | 已实现：`react-markdown` 与 `remark-gfm` 渲染 Markdown/GFM 表格，并仅为已注册语言用 highlight.js。尚未有与 Android 等价的图片 URL、链接协议和资源上限策略。 | 节点正文 `/api/nodes/{slug}`、资源 `/content-assets/{asset_path:path}`。 | 写入统一的 Markdown 安全策略：禁止不安全协议，限制资源大小/数量，保持不注入原始 HTML；加入表格、恶意链接和未知代码语言测试。 | GFM 表格可横向可读；`javascript:` 等链接不可触发；原始 HTML 不执行。源码：`app/src/components/MarkdownView.tsx`、`android-app/app/src/main/java/com/cslearningos/mobile/ui/MarkdownRenderer.kt`、`android-app/app/src/main/java/com/cslearningos/mobile/ui/markdown/MarkdownTextAnnotations.kt`。 |
| AI Provider 配置 | 已实现：用户配置 provider、HTTPS endpoint、API key 和模型；设置校验 endpoint，密钥加密保存，应用禁用明文流量。AI 可选。 | 未实现用户配置：只读取服务端 `/api/ai/preflight` 的 provider/启用状态；Web 不输入、保存或传输 provider 凭据。 | `GET /api/ai/preflight` 是可自动读取的只读配置状态；`POST /api/ai/model-preflight` 是受 `X-CS-Local-Action: 1` 保护的模型调用预检。 | 将 Web 明确为“本机后端配置”的消费者；保留自动的 GET 状态读取，仅在用户显式点击后以本机动作头发送 POST 模型预检并显示可信本机边界。若以后允许浏览器配置，必须另行定义凭据存储与 HTTPS 策略。 | 未配置时 AI 操作禁用并说明原因；GET 状态读取可自动发生且不运行模型，只有显式点击的 POST（带 `X-CS-Local-Action: 1`）能运行模型预检；浏览器控制台和持久化存储无 API key。源码：`backend/system_router.py`、`backend/test_system_router_security.py`、`app/src/App.tsx`、`android-app/app/src/main/AndroidManifest.xml`、`android-app/app/src/main/java/com/cslearningos/mobile/feature/settings/domain/ValidateAiSettingsUseCase.kt`。 |
| AI 草稿审阅 | 已实现：AI 输出是可编辑提案，用户审阅并显式保存后才写入节点/复习内容。 | 已实现：创建 AI job，展示草稿，并支持 apply、reject、retry；草稿过期时提示重新生成。 | `/api/ai/jobs`、`/api/ai/jobs/{job_id}`、`/events`、`/apply`、`/reject`、`/retry`。 | 补充“发送什么内容”和 apply 前确认；把 job 轮询/事件失败、取消和陈旧草稿写成可测状态机，禁止自动 apply。 | 生成不改变目标；仅点击 apply 后改变目标；版本变化的草稿不可 apply；reject/retry 可恢复。源码：`app/src/App.tsx`、`backend/ai_router.py`、`android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/domain/AssistantSafetyPolicy.kt`。 |
| 知识助手 | 已实现：发送问题时自动检索有限的本地上下文并发起会话；工具除本地搜索外都需确认，草稿仍需用户保存。 | 未实现：没有对应的聊天会话、有限本地检索上下文或确认工具 UI；Reader Question 与 AI job 不是知识助手。 | 现有 AI job 路由不提供知识助手会话契约。 | 先定义只读本地检索、上下文预算、会话数据和确认工具的 API 合同，再实现 Web 会话；不复用编辑 job 伪装为助手。 | 每次发送最多附带 3 条、合计最多 1,200 字符上下文；任何写操作都经确认和现有显式保存路径。源码：`android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/domain/KnowledgeAssistantSession.kt`、`AssistantSafetyPolicy.kt`。 |
| 安全限制 | 已实现：`usesCleartextTraffic=false`；AI 上下文最多 3 条/1,200 字符、单摘录最多 420 字符；备份解码有有界校验。 | 部分实现：ReactMarkdown 默认不解析原始 HTML，AI job 有显式 apply/reject；但没有统一的 Web 内容大小、链接/图片协议或上下文传输上限。 | 所有写路由；AI 预检和 job 路由；未来导入路由。 | Stage 0 先写共享限制表和客户端校验，后端重复强制；为每一类上限编写拒绝路径测试，错误不泄露密钥或完整内容。 | 超限输入在请求前被阻止且服务端再次拒绝；日志/错误不含 API key；恶意 Markdown/备份不执行或改变数据。源码：`android-app/app/src/main/AndroidManifest.xml`、`android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/domain/AssistantSafetyPolicy.kt`、`backend/ai_router.py`。 |
| 同步 | 已实现个人桌面 Study Sync：配对、scoped pull/push、Daily Bite pull、复习事件上传和 ZIP 交接；它是手机复习训练子集，不是完整桌面 KG/DB 镜像。 | 已实现本机 Sync/Health 入口和 sync API 消费；Web 仍是桌面工作台，不是离线移动端。 | `/api/sync/v1/*`、`/api/bites`、`/api/bite/extract-and-save`。 | 继续把 UI 与文档称为 Study Sync / 复习同步；不要把 full KG/frontmatter/index 兼容塞给 Android。Hosted/account sync 另立方案。 | 手机端可拉取 light nodes、quiz、bite cards 并上传学习事件；Area scope 过滤生效；断网/权限变化有错误提示。源码：`android-app/docs/architecture.md`、`docs/android-workflow.md`、`android-app/app/src/main/java/com/cslearningos/mobile/feature/sync/SyncRepository.kt`。 |

> 状态更新（2026-07-19）：个人桌面-移动同步已按 `docs/superpowers/plans/2026-07-11-personal-desktop-mobile-sync.md` 落地为 Study Sync：桌面 `sync_changes` 游标协议 + 配对/凭据 + scoped manifest/pull + Daily Bite pull + append-only push（attempts/captures/questions）+ 修订门控内容上传（`push/nodes`/`push/quizzes`）+ Android More 页手动同步 UI + ZIP 文件交接。它同步手机复习训练子集，不承诺完整桌面知识库镜像。

## 分阶段执行

### Stage 0：安全与契约

1. 为 Markdown 链接、图片、正文大小、AI 上下文和导入文件写出客户端与服务端共同的限制表，服务端作为最终裁决。
2. 将 `/api/package/export?write=true` 改为显式写入方法；保留可自动读取的 `GET /api/ai/preflight` 配置状态，只有用户显式点击且携带 `X-CS-Local-Action: 1` 的 `POST /api/ai/model-preflight` 能触发模型调用。
3. 为 API 错误、陈旧 AI 草稿、非安全 URL、超限输入和恢复失败添加前端单元测试及后端路由测试。

完成标准：副作用请求均为显式动作；未配置 AI、恶意内容或超限输入不会被误报成功，也不会隐式写入数据。

### Stage 1：本地工作流

1. 完成节点/Area 的浏览、创建、编辑、复习和回收站回归测试，复用已存在的 node 与 quiz 路由。
2. 在后端先补 Capture 和备份恢复合同及测试，再在 Web 提供 Capture 队列、提升草稿、备份预览和显式恢复。
3. 让所有本地 API 失败态说明数据仍由本机服务管理，不承诺浏览器离线模式或同步。

完成标准：在本机 FastAPI 运行时，用户可以完成创建节点、分类浏览、复习、回收站恢复、Capture 整理、导出和经确认的恢复；Capture 与恢复在相应 API 未完成前保持“计划”。

### Stage 2：可选 AI

1. 显示服务端 AI 配置状态和明确发送范围；未配置或非可信本机环境时禁用生成。
2. 固化 job 的创建、进度、失败、取消、重试、审阅、拒绝和 apply 状态，不允许生成结果自动写入。
3. 在单独的助手合同完成后，实现有限本地上下文、会话和确认工具；它不授予批量写入权限。

完成标准：每次 AI 写入都有用户可编辑草稿和显式确认；知识助手在实现前仍标为计划。

### Stage 3：延期的同步、富渲染与批量写入

同步、账号、跨设备冲突解决、语义检索、公式/代码专用阅读器和助手批量写入均延期。它们不是当前 Android 或 React Web 能力，也没有本阶段实现日期。开始前必须先通过独立设计评审确定数据所有权、权限、失败恢复和验收测试。

## 路由与源码核验清单

实现每个阶段前，用下列检索确认路由和文件仍存在；本计划中的路径均以当前工作树为准：

```powershell
rg -n "@(app|router)\\.(get|post|put|patch|delete)" backend
rg -n "'/api/(nodes|areas|review/due|ai/jobs|ai/preflight|package/export)" app/src/App.tsx
rg -n "(BackupCodec|AssistantSafetyLimits|isSafeMarkdownDestination|SaveNodeCommand)" android-app
git diff --check
```

如果路由改变，先更新矩阵的“FastAPI 依赖”和相应验收证据，再开始 UI 工作。
