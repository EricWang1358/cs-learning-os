# RFC: KnowledgeGraph 深模块 —— 问题为根的知识树 OS

> 状态: 已评审(三方案对比) · 推荐混合方案 · 作为 Android(Kotlin/Room)、Backend(FastAPI)、Desktop(React 3D) 三端实现的统一契约
> 方法: code-arch-optimizer(深模块深化, RFC 模板)

## 1. 问题(架构摩擦)

现有 CS Learning OS 的 `learning_nodes` 是**平铺**的: 无"问题(Question)"概念、无知识点间的前置依赖、
无掌握度投影。导致:

- 用户刷题卡壳(如 LeetCode DP 题)时, 助手只能给一次性对话回答, 无法"往前追溯前置知识直到 C++ 底层";
- 复习答错时只能看到"这道题错了", 无法定位漏洞在哪个前置知识点、它阻塞了多少下游;
- 不同问题重复学过的知识点无法复用, 学习轨迹是线性的而非网状;
- JD → 120 题 → 专家进度 这类批量学习战役无数据结构承载。

**候选深化点评估**: ① KnowledgeGraph(本 RFC, 核心) ② 助手会话编排(已较稳定, 不动) ③ 同步 Outbox(Phase 2A 已设计, 复用而非深化)。选择 ①。

## 2. 三个候选方案与决策

| 维度 | A 最小接口 | B 最大灵活性 | C 默认场景极简 |
|---|---|---|---|
| 公开入口 | 单 Facade(13 方法) | 命令/查询分离, ~15 抽象 | 4 个一行方法 + advanced |
| 部分继承建模 | attach + 追加分支(命令级) | **EdgeScope(Global/ProblemLocal) 单表**(最优) | LinkMode REFERENCE/COPY(拷贝) |
| AI 信任门禁 | Proposal/Confirm 两段式(最强) | EdgeStatus PENDING(边级) | Preview/Confirm(预览级) |
| 瓶颈诊断 | SQL 聚合 | 策略化 | **规则引擎, 离线可用**(最优) |
| 风险 | god interface | 过度设计(自认, 附 MVP 裁剪) | 默认值即契约 |

**决策(混合)**: 骨架 = A 的单 Facade + Proposal/Confirm 门禁; 边模型 = B 的 EdgeScope 单表; 读侧 = C 的 UI 直渲 DTO + 规则引擎瓶颈诊断(离线); 掌握度策略保留接口但 v1 只内置一个规则引擎(B 的 MVP 裁剪结论)。否决: 谓词 DSL、事件流公开化、传递闭包表(v1 用 recursive CTE)、全量事件溯源、COPY 式继承(拷贝破坏"改共享子树全体生效")。

## 3. 最终契约(三端统一)

### 3.1 心智模型与不变量

- **全局一张 DAG**: 节点复用 `learning_nodes`; 边 `parent → child` 表示 "parent 依赖 child"(child 是前置)。
- **Question = 登记的 root**: `kg_question` 把某 `learning_node` 注册为带独立序号的问题根; `(area_id, problem_no)` 唯一, 序号只增不改。
- **树 = 视图**: 问题 P 的树 = 从 root 沿 `GLOBAL ∪ PROBLEM_LOCAL(P)` 边可达的子图。共享子树 = 可达集相交(零拷贝); 部分继承 = Global 边引用已有子树 + ProblemLocal 私有分支。
- **reroot 是纯查询**, 任意节点可当根, 零写入。
- **AI 产出永不直接生效**: 一律经 Proposal(带 TTL) → 用户确认 → 幂等落库。
- 不变量: Global 边插入时在 Global ∪ 全部 Local 边集上查环(保守规则, ADR-1); 所有写走幂等命令(commandId+fingerprint, 同 processed_commands 语义)且与 replication_outbox 同事务; mastery 是可重建投影(quiz 证据为事实源)。

### 3.2 Kotlin 公开面(application:graph)

```kotlin
// 复用 core:kernel: NodeId, CommandId, QuizItemId, AreaId, DomainResult, Clock, IdGenerator
@JvmInline value class QuestionId(val value: String)
@JvmInline value class EdgeId(val value: String)
@JvmInline value class ProposalId(val value: String)
@JvmInline value class JdBatchId(val value: String)

enum class KgCategory { CS_BASIC, ALGORITHM, SYSTEM_DESIGN, BEHAVIORAL }
enum class EdgeScope { GLOBAL, PROBLEM_LOCAL }
enum class MasteryState { UNKNOWN, LEARNING, FRAGILE, MASTERED }
enum class Verdict { PASS, FAIL }

sealed interface KgError {
    data class CycleDetected(val path: List<String>) : KgError
    data class CommandConflict(val commandId: String) : KgError
    data class NotFound(val kind: String, val id: String) : KgError
    data class ProposalExpired(val proposalId: String) : KgError
    data class ProposalShapeInvalid(val reason: String) : KgError
    data class Storage(val message: String) : KgError
}

/** 嵌套前置结构: 同时表达多层链与"引用已有节点"(共享子树) */
data class PrerequisiteSpec(
    val title: String,
    val existingNodeId: String? = null,   // 复用已有 learning_node → 共享
    val markdownBody: String = "",
    val children: List<PrerequisiteSpec> = emptyList(),
)

interface KnowledgeGraph {
    // ---- 写(幂等; 同事务追加 replication_outbox) ----
    suspend fun createQuestion(commandId: String, title: String, category: KgCategory,
        areaId: String?, problemNo: Int? = null, jdBatchId: String? = null): DomainResult<QuestionSummary>
    suspend fun appendPrerequisites(commandId: String, parentNodeId: String,
        specs: List<PrerequisiteSpec>, scope: EdgeScope = EdgeScope.GLOBAL,
        scopeQuestionId: String? = null): DomainResult<TreeSnapshot>
    suspend fun detachEdge(commandId: String, edgeId: String): DomainResult<Unit>
    suspend fun recordVerification(commandId: String, nodeId: String,
        quizItemId: String, verdict: Verdict): DomainResult<MasteryUpdate>
    suspend fun confirmProposal(commandId: String, proposalId: String,
        editedPayloadJson: String? = null): DomainResult<ConfirmResult>

    // ---- AI 建议(不落库) ----
    suspend fun proposePrerequisiteChain(nodeId: String, questionText: String,
        maxDepth: Int = 3, maxBreadth: Int = 5): DomainResult<PrerequisiteProposal>
    suspend fun proposeJdDecomposition(jdText: String, categories: Int = 4,
        questionsPerCategory: Int = 30): DomainResult<JdProposal>

    // ---- 读(UI 直渲) ----
    suspend fun treeOf(questionId: String): DomainResult<TreeSnapshot>
    suspend fun subtreeOf(nodeId: String, maxDepth: Int = 32): DomainResult<TreeSnapshot>  // reroot
    suspend fun diagnoseGap(quizItemId: String): DomainResult<GapDiagnosis>                // 规则引擎, 离线
    suspend fun bottlenecks(minDistinctQuestions: Int = 2, limit: Int = 20): DomainResult<List<BottleneckNode>>
    suspend fun expertiseProgress(jdBatchId: String): DomainResult<ExpertiseProgress>
    suspend fun export3d(root: String, rootIsQuestion: Boolean): DomainResult<GraphExport>
    fun observeBottlenecks(limit: Int = 5): Flow<List<BottleneckNode>>
}
```

DTO(读模型): `QuestionSummary{questionId, rootNodeId, problemNo, title, category}`,
`TreeSnapshot{rootNodeId, questionId?, nodes[KGNodeDto], edges[KGEdgeDto], layers[[nodeId]], sharedNodeIds}`,
`KGNodeDto{nodeId,title,depth,parentCount,mastery}` / `KGEdgeDto{edgeId,parent,child,scope}`,
`MasteryUpdate{nodeId, mastery, suggestedNext[]}`,
`GapDiagnosis{quizItemId, failedNodeId, weakestPrerequisite{nodeId,title,mastery,blocksCount,recentFailures}?, suggestedAction}`,
`BottleneckNode{nodeId,title,mastery,blocksCount,distinctQuestionCount,recentFailures}`,
`ExpertiseProgress{total, mastered, fragile, learning, unknown, progress, perCategory}`,
`ConfirmResult{createdQuestionIds, createdNodeIds, reusedNodeIds}`,
`PrerequisiteProposal{proposalId, tree: PrerequisiteSpec, reusedNodeIds, expiresAt}`,
`JdProposal{proposalId, batchId, questions[{category,seq,title,seedPrerequisites[]}], expiresAt}`,
`GraphExport{payloadJson, contentHash, nodeCount, edgeCount}`。

### 3.3 Schema v9(纯增量, 只 CREATE; Room 与 SQLite/FastAPI 共用此 DDL)

```sql
CREATE TABLE IF NOT EXISTS kg_question (
  question_id TEXT PRIMARY KEY NOT NULL,
  root_node_id TEXT NOT NULL REFERENCES learning_nodes(id),
  area_id TEXT REFERENCES areas(id),
  problem_no INTEGER NOT NULL,
  title TEXT NOT NULL,
  category TEXT NOT NULL DEFAULT 'CS_BASIC',
  jd_batch_id TEXT,
  status TEXT NOT NULL DEFAULT 'ACTIVE',          -- ACTIVE | ARCHIVED
  revision INTEGER NOT NULL DEFAULT 1,
  created_at INTEGER NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_kg_question_areano
  ON kg_question(COALESCE(area_id,''), problem_no) WHERE status != 'ARCHIVED';

CREATE TABLE IF NOT EXISTS kg_edge (
  edge_id TEXT PRIMARY KEY NOT NULL,
  parent_node_id TEXT NOT NULL REFERENCES learning_nodes(id),   -- parent 依赖 child
  child_node_id  TEXT NOT NULL REFERENCES learning_nodes(id),
  scope_type TEXT NOT NULL DEFAULT 'GLOBAL',      -- GLOBAL | PROBLEM_LOCAL
  scope_question_id TEXT REFERENCES kg_question(question_id),
  status TEXT NOT NULL DEFAULT 'ACTIVE',          -- ACTIVE | PENDING_CONFIRMATION | REJECTED
  created_by TEXT NOT NULL DEFAULT 'USER',        -- USER | AI | IMPORT
  revision INTEGER NOT NULL DEFAULT 1,
  created_at INTEGER NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_kg_edge_live
  ON kg_edge(parent_node_id, child_node_id, scope_type, COALESCE(scope_question_id,''))
  WHERE status != 'REJECTED';
CREATE INDEX IF NOT EXISTS idx_kg_edge_parent ON kg_edge(parent_node_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_kg_edge_child  ON kg_edge(child_node_id)  WHERE status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS kg_proposal (
  proposal_id TEXT PRIMARY KEY NOT NULL,
  kind TEXT NOT NULL,                              -- PREREQUISITE_CHAIN | JD_DECOMPOSITION
  payload_json TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING',          -- PENDING | CONFIRMED | REJECTED | EXPIRED
  model_ref TEXT, command_id TEXT,
  expires_at INTEGER NOT NULL, created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS kg_mastery (
  node_id TEXT PRIMARY KEY NOT NULL REFERENCES learning_nodes(id),
  state TEXT NOT NULL DEFAULT 'UNKNOWN',           -- UNKNOWN|LEARNING|FRAGILE|MASTERED
  score REAL NOT NULL DEFAULT 0.0,
  attempts INTEGER NOT NULL DEFAULT 0,
  fail_streak INTEGER NOT NULL DEFAULT 0,
  last_verdict TEXT, updated_at INTEGER NOT NULL,
  revision INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS kg_mastery_event (
  event_id TEXT PRIMARY KEY NOT NULL,
  node_id TEXT NOT NULL, quiz_item_id TEXT NOT NULL,
  verdict TEXT NOT NULL, command_id TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
```

软删/revision 列保证 outbox 可表达"删边"; `processed_commands`/`replication_outbox`/`node_fts` 原样复用。

### 3.4 3D 导出 JSON 契约(backend 与 React 共用, schemaVersion=1)

```json
{
  "schemaVersion": 1,
  "contentHash": "sha256...",
  "generatedAt": 1780000000000,
  "nodes": [{"id":"n1","title":"LC 300 LIS","layer":0,"mastery":"FRAGILE","score":0.4,
             "parentCount":0,"sharedByQuestions":2,"isRoot":true}],
  "links": [{"source":"n1","target":"n2","scope":"GLOBAL"}]
}
```
`layer` = 从 root 的最长拓扑距离(服务端算好, 前端力导布局只用它着色/分层); 输出按 `(layer, id)` 稳定排序。

### 3.5 瓶颈/掌握度规则引擎(离线, v1 默认实现)

- verdict FAIL → `fail_streak+1`; score = max(0, score-0.25); state: attempts>=1 → FRAGILE(若 fail_streak>=1) 否则 LEARNING。
- verdict PASS → fail_streak=0; score=min(1, score+0.2); state: score>=0.8 **且含本次在内的最近 3 次 verdict 无 FAIL** → MASTERED, 否则 LEARNING(注: 从 score=0 起需连续 4 次 PASS 才 MASTERED; 近 3 次窗口含 FAIL 时即使 score=0.95 也保持 LEARNING)。
- diagnoseGap(quiz): 取 quiz.node_id 的直接前置中 (1-mastery)×(1+blocksCount) 最高者, suggestedAction=`REINFORCE_PREREQUISITE`; 无前置 → `LEAF_REINFORCE`。
- bottlenecks(v1.0 定稿语义): 弱节点 = FRAGILE/LEARNING 且 score<0.6。**候选集** = 每棵问题树可见边集(GLOBAL ∪ PROBLEM_LOCAL(该问题))内, 每个弱节点的自反祖先闭包(含弱节点自身+全部可达祖先, 祖先可为非弱节点——漏洞根因常在前置祖先)。**dependentFailCount** = 包含该候选的不同问题棵数。**排序**: dependentFailCount 降序 → blocksCount 降序 → nodeId 升序; 门槛 ≥ minDistinctQuestions(默认 2)。
- blocksCount = 节点反向可达下游节点数(在 GLOBAL ∪ 全部 LOCAL 边集上统计; 懒算 + kg 变更时缓存失效)。
- export3d: nodes 按 (layer,id) 升序, links 按 (source,target,scope) 升序; contentHash 只需单端内稳定(跨端算法不做统一要求)。

### 3.6 REST 契约(FastAPI, 与 Kotlin facade 一一对应)

```
POST /graph/questions                      createQuestion
POST /graph/questions/{qid}/prerequisites  appendPrerequisites
POST /graph/proposals/prerequisite-chain   proposePrerequisiteChain
POST /graph/proposals/jd-decomposition     proposeJdDecomposition
POST /graph/proposals/{pid}/confirm        confirmProposal
POST /graph/verifications                  recordVerification
GET  /graph/questions/{qid}/tree           treeOf
GET  /graph/nodes/{nid}/subtree            subtreeOf(reroot)
GET  /graph/quizzes/{qid}/gap              diagnoseGap
GET  /graph/bottlenecks                    bottlenecks
GET  /graph/batches/{bid}/progress         expertiseProgress
GET  /graph/export3d?root=...&rootIsQuestion=...
DELETE /graph/edges/{eid}                  detachEdge
```

## 4. 依赖策略

- **进程内**: core:kernel 值类型、纯函数算法(环检测、Kahn 分层、规则引擎、fingerprint canonicalizer)——直依赖, 纯函数单测。
- **本地可替代**: KgStore 持久化端口 → 默认 data:graph-room(Room v9); 测试用 InMemoryKgStore 双跑同一契约测试。
- **端口与适配器**: SaveNodeCommand(application:content 复用其 FTS/outbox 事务管线)、ModelGateway(feature:assistant, AI 建议唯一来源)、OutboxAppender、Clock/IdGenerator——只 import 端口, 组合根注入。
- **Mock**: FakeModelGateway(canned proposal)、InMemoryKgStore、FixedClock。

## 5. 测试策略(替代而非叠加)

新边界测试: ①幂等重放(同 commandId 同指纹复用/异指纹拒绝) ②环检测(含菱形误报用例) ③ProblemLocal 隔离(B 的私有分支不进 A 的视图) ④共享子树修改双树可见 ⑤Proposal 过期/确认/编辑落库 ⑥瓶颈排序与"≥2 棵树"门槛 ⑦JD 120 树断点续跑 ⑧导出 JSON 契约稳定(contentHash)。环境: Room in-memory + JVM 单测; 无 Android 仪器测试需求。浅层旧测试无需删除(纯新增模块)。

## 6. 实施建议与分期

- **MVP(v1)**: 本 RFC 全部写路径 + treeOf/subtreeOf/diagnoseGap/bottlenecks/export3d; MasteryPolicy 硬编码规则引擎; observeBottlenecks。
- **v1.1**: MasteryPolicy 可插拔(SM-2 联动策略)、瓶颈缓存物化、Desktop 图谱编辑器。
- **v2**: 协作/共享图谱(outbox 已留数据)、传递闭包表(压测不过再升级)、课程大纲导入。
- 调用方迁移: AssistantCoordinator 的"卡壳"场景 → proposePrerequisiteChain + 确认卡片 → confirmProposal; 复习答错回调 → recordVerification + diagnoseGap; Dashboard → observeBottlenecks; Desktop → GET /graph/export3d。
- ADR-1: 保守环检测(Global 插入查 Global∪全部Local)可能误拒极少合法场景, 以用户可读错误文案兜底。
