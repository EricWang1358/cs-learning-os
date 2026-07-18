# Plan — CS Learning OS 知识树/知识图谱架构优化与实现

## 任务理解
用户要把"以问题为根、知识点为节点的可交叉引用知识树(DAG)"学习管理架构实现出来，
与其现有项目(Android Compose+Room schema v8 / Desktop React / FastAPI backend)兼容。
功能清单：
1. 问题序号 → 独立 root 树；树间共享/继承子树(DAG 而非线性)
2. 前置知识追溯(叶子→根，prerequisite 链)、reroot 延伸、3D 可视化
3. 学习闭环：学完→提问验证→漏洞定位(bottleneck/gap)→继续补树→直到掌握
4. JD 拆解 → 120 题 → 120 棵树；掌握度汇总
5. 可扩展性、local-first、与现有幂等命令/Outbox 同步模式兼容
6. 用户没想到的增强：掌握度传播、瓶颈节点定位、学习路径规划(拓扑排序)、
   循环依赖检测、掌握度衰减与复习(SM-2)联动、图谱导出

## 阶段划分

### Stage 1 — 架构设计(技能: code-arch-optimizer)
- 已读 SKILL.md + REFERENCE.md(深模块/RFC 模板/依赖四分类)
- 因无真实代码库，以用户提供的技术说明书为"代码库替代物"做架构摩擦分析
- 并行 3 个设计子代理，各出一个截然不同的"KnowledgeGraph 深模块"接口方案：
  - A: 最小接口(1-3 入口)
  - B: 最大灵活性(多用例/扩展点)
  - C: 面向最常见调用方优化(默认场景极简)
- Orchestrator 对比分析 → 给出有立场的推荐(可混合) → 产出 GitHub Issue 风格 RFC
- 输出: docs/RFC-knowledge-graph.md

### Stage 2 — 并行实现(4 个 coder 子代理，互相独立)
- A: Kotlin 纯领域模块 :domain:graph(GraphEngine/MasteryEngine/PathPlanner + 单测)
- B: Room 数据层(schema v8→v9 迁移、实体/DAO、幂等命令适配器，仿 SaveNodeCommand)
- C: FastAPI backend graph_service 扩展(REST + pytest，可实际运行验证)
- D: Desktop React 3D 可视化组件(react-force-graph-3d + 图谱导出 JSON 契约)
- 各代理收到的契约：RFC 推荐方案 + 数据模型 + 现有项目约定(模块依赖方向、幂等、Outbox)

### Stage 3 — 验证
- 实际运行 backend pytest(环境内可执行)
- reviewer 子代理跨检：Kotlin/Room/SQL 迁移一致性与 schema v8 约定兼容性

### Stage 4 — 集成交付
- 当前原型归档于 `experimental/knowledge-graph-prototype/`。
- 交付：RFC + 四层代码 + 测试报告 + README(集成指南)
- 打包 zip

## 文件传播(A2A)
RFC(Stage1) → 4 个 coder 提示词(Stage2) → 验证结果(Stage3) → 修复/集成(Stage4)
