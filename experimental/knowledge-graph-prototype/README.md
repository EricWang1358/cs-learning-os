# CS Learning OS — KnowledgeGraph(知识树 OS)模块包

以"问题为根、知识点为节点"的可交叉引用知识树(DAG)学习管理架构的完整实现，
与现有 CS Learning OS(Android Compose + Room schema v8 / Desktop React / FastAPI)兼容。

## 包结构

```
experimental/knowledge-graph-prototype/
├── docs/RFC-knowledge-graph.md     # 架构 RFC: 三方案对比、混合推荐、三端统一契约(单一事实源)
├── android/
│   ├── domain-graph/               # :domain:graph 纯 Kotlin 领域模块(零 Android 依赖)
│   │                               #   GraphEngine(环检测/最长路径分层/树物化/瓶颈) 
│   │                               #   MasteryEngine(规则引擎) Fingerprint(幂等) 
│   │                               #   KnowledgeGraphService(内存 facade, 53 个 JUnit 测试)
│   └── data-graph-room/            # :data:graph-room Room 适配层
│                                   #   5 实体/4 DAO/MIGRATION_8_9(纯增量)/事务管线(命令+outbox)
├── backend/                        # FastAPI 图谱服务(13 端点, 16 个 pytest 全绿)
│   ├── graph_engine.py             #   纯 Python 图算法 + 规则引擎(与 Kotlin 端语义对齐)
│   ├── graph_store.py              #   SQLite 持久化 + 幂等事务管线
│   ├── graph_service.py            #   APIRouter + create_app(db_path)
│   └── test_graph_service.py
└── desktop/graph3d/                # React 3D 可视化组件包(react-force-graph-3d)
    └── src/                        #   KnowledgeGraph3D 组件/类型/fetchGraph(contentHash 增量)
```

## 已实现功能对照

| 需求 | 实现 |
|---|---|
| 问题独立序号 → 树 root | `kg_question((area_id, problem_no) 唯一)` + `createQuestion` / `POST /graph/questions` |
| 前置知识多层追溯 | `PrerequisiteSpec` 嵌套结构 + `proposePrerequisiteChain`(AI 建议) → 确认卡片 → `confirmProposal` 落库 |
| reroot 延伸 | `subtreeOf(nodeId)` / `GET /graph/nodes/{nid}/subtree`(纯查询, 零写入) |
| 共享子树 / 部分继承(DAG) | `kg_edge.scope_type`: GLOBAL(共享, 改一处全树可见) + PROBLEM_LOCAL(问题私有分支) |
| 学习闭环验证 | `recordVerification`(quiz 判定 → 掌握度投影) + `suggestedNext` 引导继续补树 |
| 漏洞定位 | `diagnoseGap`(答错 → 定位最薄弱前置) + `bottlenecks`(被 ≥2 棵树的弱节点依赖的瓶颈知识点) |
| 掌握度状态机 | UNKNOWN/LEARNING/FRAGILE/MASTERED 规则引擎(近 3 次无 FAIL 且 score≥0.8 → MASTERED), 离线可用 |
| JD → 120 棵树 | `proposeJdDecomposition`(4 类 × 30 题) → 确认 → 批量建树 + `expertiseProgress` 专家进度(含 perCategory) |
| 3D 可视化 | `export3d`(layer 服务端算好) → React `KnowledgeGraph3D`(掌握度配色/共享双环/LOCAL 虚线/点击 reroot) |
| 架构约束 | 幂等命令(commandId+fingerprint, 重放复用/冲突拒绝)、事务内追加 replication_outbox、schema v8→v9 纯增量、AI 产出确认前不落库 |
| **没想到的增强** | 环检测(保守规则 ADR-1, 返回成环路径)、菱形多父最长路径分层、共享边统计(sharedNodeIds/sharedByQuestions)、contentHash 增量刷新(前端不重渲染)、掌握度可重建投影(事件溯源 kg_mastery_event)、瓶颈离线规则引擎(不依赖 AI)、ProblemLocal 隔离防跨树污染 |

## 集成到现有项目

### Android
1. `settings.gradle` 加入 `:domain:graph`、`:data:graph-room`。
2. 宿主 `LearningDatabase`: version 8→9, entities 追加 5 个 kg 实体, `addMigrations(MIGRATION_8_9)`, `addCallback(GraphSchemaV9.freshInstallCallback)`, 并 `implements KgDaoProvider`。
3. 组合根: `KnowledgeGraphDeps` 注入 `RoomKgStore`、`SaveNodeCommand`(现有端口)、`ModelGateway`(现有)、Outbox 桥接 `ProcessedCommandPort/OutboxPort`。
4. **已知接缝**: `KnowledgeGraphService` 当前绑定内存 store；接 Room 时需在 application 层把 store 抽象为接口(Room 侧 `KgStore` 端口已按此接缝设计, 裸 String↔value class 由 `KgMappers` 转换)。

### Backend
`from graph_service import create_app` 或将 `router` include 进现有 `api.py`；数据库与 Android 共用 schema v9 DDL(`graph_store.py` 内含 learning_nodes 等占位表, 对接时改为复用真实表)。

### Desktop
`desktop/graph3d/README.md` 有完整对接示例：`fetchExport3d(apiBase, root, rootIsQuestion, lastHash)` + `<KnowledgeGraph3D data={...} onNodeClick={reroot} />`。

## 验证状态

- Kotlin 领域模块: kotlinc 1.9.24 编译零错误, **53/53 JUnit 测试通过**
- Room 模块: KSP/room-compiler 实跑零错误, schema v9 迁移经离线 validateMigration 仿真比对一致
- Backend: **16/16 pytest 通过**, uvicorn 真实启动冒烟通过
- Desktop: TypeScript strict `tsc --noEmit` 零错误, 33/33 行为测试通过
- 三端契约一致性: 独立 verifier 交叉检查通过(Schema/REST/JSON/枚举/公式)

## 已知余项(非阻断)

1. `contentHash` 算法跨端不统一(单端内稳定, RFC 允许)。
2. 后端 bottlenecks 响应的 `recentFailures` 为全量 FAIL 计数, Kotlin 端为近 3 次窗口(v1.1 统一)。
3. three.js 自定义视觉(双环/光晕)未经浏览器实渲, 参数可能需微调。
4. MVP 之后路线: MasteryPolicy 可插拔(SM-2 联动)、瓶颈缓存物化、传递闭包表、协作共享图谱(见 RFC §6)。
