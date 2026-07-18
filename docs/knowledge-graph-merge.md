# KnowledgeGraph 合并记录 — 问题为根的知识树 OS

> 来源: `experimental/knowledge-graph-prototype/` 模块包(RFC + 三端实现, 见包内 `docs/RFC-knowledge-graph.md`)。
> 本文档记录: 插入逻辑审计结论、实际合并步骤、双端入口、已知接缝与后续阶段。

## 1. 审计结论(插入逻辑是否合理)

### 包本身设计合理、原样保留的部分

- **RFC 三端统一契约**(Schema/REST/JSON/枚举/规则引擎公式)——单一事实源, 三端 parity 可验证。
- **Schema v9 纯增量 DDL**(只 CREATE, 不动旧表) + 幂等命令管线(commandId+fingerprint,
  同事务 outbox) + Proposal/Confirm 两段门禁(AI 产出确认前不落库)。
- **Android 侧假设与真实工程完全吻合**: `LearningDatabase` 正是 Room v8,
  `learning_nodes`/`quiz_items`/`areas`/`processed_commands`/`replication_outbox` 全部存在;
  端口桥接(`ProcessedCommandPort`/`OutboxPort`/`KgDaoProvider`)避免跨模块 DAO 耦合;
  Room TableInfo 校验的三处受控偏差(跨模块 FK 裁剪/镜像索引/freshInstallCallback)处理正确。
- 桌面 3D 组件的 contentHash 增量刷新与契约校验。

### 合并时修正的 6 个插入点

| # | 问题 | 修正 |
|---|---|---|
| 1 | 包 `backend/graph_service.py` 与现有 `backend/graph_service.py`(导航图谱)同名冲突 | 改名 `kg_engine.py` / `kg_store.py` / `kg_router.py` |
| 2 | 包路由挂根 `/graph/*`, 与 `/api/graph` 导航路由语义混淆、缺 `/api` 前缀 | 统一挂载 **`/api/kg/*`** |
| 3 | 占位表照 Android schema 画(`learning_nodes`/`areas`/`quiz_items`), 桌面后端真实表是 `nodes(slug)`/`quizzes(id)`/`quiz_links` | 映射到真实表; 幂等/outbox 用自包含 `kg_processed_commands`/`kg_outbox`; quiz→node 绑定走 `quiz_links` 优先、`kg_mastery_event` 最近事件兜底 |
| 4 | ingest 每次全量 `DELETE FROM nodes` 重建(dev.ps1 每次启动都跑), DB-only 节点会被冲掉; kg 表对 `nodes` 建硬 FK 会挡住 ingest | **kg 新建节点 = 真实 markdown 文件**(`content_root/nodes/<area>/<slug>.md`, 复用 `node_lifecycle_service` 模板与同事务 upsert, 自动进 `sync_changes` 可同步手机); kg 表不建跨模块 FK |
| 5 | JD 物化 `area_id = "{batchId}:{category}"` 含 `:`, Windows 非法目录名 | `slugify` 为 `jd-<batch8>-<category>`, 批次命名空间不变 |
| 6 | Android 构建脚本是 Kotlin DSL + 硬编码版本, 真实工程是 Groovy + 版本目录 + 约定插件 | 按真实约定重写两个模块 build 文件; 版本目录补 `kotlinx-serialization-json` 与 JUnit5 条目; 根 build.gradle 补 serialization 插件 |
| 7 | **合并中实测发现**: RFC 原文的 `COALESCE` 表达式唯一索引需 SQLite 3.9+, Robolectric(sqlite4java 3.8.7)与 minSdk 26 旧设备上直接 syntax error——包的"离线仿真"未能暴露 | 拆成两个"纯列 + WHERE 谓词" partial unique 索引(NULL 桶/按值桶各一), NULL 语义不变、三端 DDL 同步改写; 记为 Room 侧**受控偏差 #4** |

另有一个功能性补充: RFC 的 13 端点没有"列出全部问题", 桌面页入口需要,
新增 **`GET /api/kg/questions`**(纯增量, 见 kg_router.py 标注)。

## 2. 合并后的形态

### Backend(桌面 FastAPI, `backend/`)

- `kg_engine.py` — 纯算法(环检测/最长路径分层/树物化/规则引擎/启发式提案), 包内原样。
- `kg_store.py` — `KgGraphStore`: 单连接 SQLite(共享 knowledge.db), kg 五表 +
  `kg_processed_commands` + `kg_outbox` 自包含管线; 初始化时幂等执行核心 schema + kg DDL。
- `kg_router.py` — 14 端点挂在 `/api/kg`(13 个 RFC 端点 + `GET /questions` 列表)。
  kg 节点 id **就是** nodes.slug; 新建节点落真实 markdown(面积: `knowledge-graph` 或
  `jd-<batch8>-<category>`, track=`kg`, status=draft, visibility=support),
  与普通笔记同列表可编辑、ingest 重建后仍存活、经 sync_changes 同步到手机。
- `api.py` — 启动时创建 `KgGraphStore(DB_PATH)` + 注册 KgError 处理器与路由。
- `test_kg_service.py` — 16/16 通过(契约测试: 幂等重放/冲突、环检测含菱形不误报、
  ProblemLocal 隔离、共享子树双树可见、Proposal 过期/确认/编辑落库、规则引擎公式、
  瓶颈排序与 ≥2 棵门槛、JD 120 题与进度、contentHash 稳定、软删边消失)。
  全量 backend 套件 73/73 通过, 导航图谱路由 `/api/graph*` 不受影响。

### Desktop(React, `app/`)

- `src/graph3d/` — 包内组件源码并入(类型/fetchGraph/nodePresentation/KnowledgeGraph3D/
  Legend/sampleData); `fetchGraph` 指向 `/api/kg/export3d`; React 19 兼容修正一处
  (`useRef` 需显式初值)。新增依赖 `three` + `react-force-graph-3d`。
- `src/components/KnowledgeGraphPanel.tsx` — 自包含页面(左侧: 问题根列表 + 新建问题 +
  瓶颈列表; 右侧: 3D 画布)。点节点 = reroot(纯查询); 点瓶颈 = 跳到弱节点。
- 入口: 侧栏 **"Knowledge tree 3D"** → 路由 `/knowledge-graph`(ViewMode `kgraph`)。
  面板经 `React.lazy` 按需加载, three.js(~1.4 MB chunk)不进主 bundle。
- 验证: `tsc -b` 零错误; `npm run build` 通过(主 chunk 333 kB 不变)。
  注: `npm run test:smoke` 在 Knowledge navigator 点击步骤失败——经 stash 对照验证
  **为合并前就存在的失败**(HEAD 代码同样失败), 与本次无关。

### Android(`android-app/`)

- `domain/graph/`(原 domain-graph)— 纯 Kotlin 领域模块, `cslearningos.kotlin.library`
  约定插件; 53 个 JUnit5 测试(本模块单独启用 jupiter 平台, 与工程 JUnit4 并存)。
- `data/graph-room/`(原 data-graph-room)— Room 适配层, Groovy 构建脚本对齐工程约定
  (compileSdk 35 / room 2.6.1 / ksp / kotlinx-serialization 1.7.3)。
- `core:database`:
  - `LearningDatabase` v8 → **v9**: entities += 5 kg 实体, `addMigrations(MIGRATION_8_9)`,
    `addCallback(GraphSchemaV9.freshInstallCallback)`, `implements KgDaoProvider`(4 个
    abstract override DAO getter, Room 生成实现)。
  - 新增 `KgGraphBridge.kt`: `KgProcessedCommandBridge` / `KgOutboxBridge`(接现有
    processed_commands / replication_outbox 表, 与 SaveNodeCommand 同纪律) +
    `KgGraphDeps.createStore(db)` 一行装配。
  - **已知偏差(有意)**: `replication_outbox.command_id` 有唯一索引而 kg 一笔命令可投影
    多行 outbox, 故 kg 行的 command_id 用每行唯一代理值 `kg:<changeId>`; KG_* 聚合
    不被现有 sync worker 消费(它只认 `content.node`/`content.quiz`), 保持 pending
    直到 kg 图结构同步阶段。
- `settings.gradle` 注册 `:domain:graph`、`:data:graph-room`; `core:database` 依赖后者。
- **UI 入口(后续任务)**: RFC §6 的调用方迁移——AssistantCoordinator 卡壳场景 →
  proposePrerequisiteChain + 确认卡片; 复习答错回调 → recordVerification + diagnoseGap;
  Dashboard → observeBottlenecks。组合根: `KgGraphDeps.createStore(LearningDatabase.create(context))`。

## 3. 已知接缝(非阻断, 后续阶段)

1. **kg 图结构跨端同步**: kg-created 的**节点内容**已可经 sync_changes 同步(实体类型
   `node`); 但 kg_question/kg_edge/kg_mastery 等图结构需要扩展 `sync_changes` 的
   `entity_type` CHECK 约束(需表重建)与同步协议范围——独立阶段设计。
2. **AI 提案来源**: propose 端点当前注入确定性启发式生成器(离线可用、测试可重现);
   生产环境应接 ModelGateway(feature:assistant)/Codex——RFC §4 端口已预留。
3. **掌握度联动**: MasteryPolicy 硬编码规则引擎; v1.1 做可插拔(SM-2/review_queue 联动),
   quiz_attempts 落库时自动发 recordVerification。
4. **桌面 Proposal/编辑 UI**: 当前桌面页覆盖建题/3D/reroot/瓶颈; 提案确认卡片与
   JD 拆解向导待做(后端端点已就绪)。
5. **contentHash** 跨端算法不统一(RFC 允许, 单端内稳定)。
6. 后端 bottlenecks 的 `recentFailures` 为全量 FAIL 计数, Kotlin 端为近 3 次窗口
   (v1.1 统一)——继承自包的已知余项。

## 4. 复跑验证

```powershell
# Backend 契约测试
.\.venv\Scripts\python.exe -m pytest backend/test_kg_service.py -q     # 16 passed
.\.venv\Scripts\python.exe -m pytest backend/ -q                       # 73 passed

# Desktop
cd app; npx tsc -b; npm run build

# Android(需要 Android SDK; 本机用 Android Studio 自带 JBR 21)
cd android-app
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :domain:graph:test                                       # 53/53 JUnit5 通过
.\gradlew.bat :data:graph-room:testDebugUnitTest                       # 14/14 Robolectric 通过
.\gradlew.bat :core:database:testDebugUnitTest                         # 3/3 迁移回归通过(含 8→9)
```

> 合并中实测发现并修复(包内未暴露): ①表达式唯一索引在 SQLite 3.8 环境
> (Robolectric sqlite4java / 旧设备)不可运行 → 受控偏差 4 便携改写;
> ②KgRoomTest 两处单表达式方法返回非 void(JUnit4 校验拒绝) → 显式 `: Unit`;
> ③visibleEdges 测试挂 LOCAL 边前未登记 question(本模块内 FK) → 先插问题根。
