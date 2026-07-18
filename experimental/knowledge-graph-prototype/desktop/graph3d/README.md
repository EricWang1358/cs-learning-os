# @cs-learning-os/graph3d

Desktop 端 3D 知识图谱可视化组件包。React 18 + TypeScript(strict) + `react-force-graph-3d` 渲染,
实现 `docs/RFC-knowledge-graph.md` 的 **§3.4 3D 导出 JSON 契约** 与 **§3.6 REST** 对接。

- 数据源: `GET /graph/export3d?root=...&rootIsQuestion=...`(FastAPI), 支持 contentHash 增量刷新与本地静态 JSON(离线开发)
- 渲染: 掌握度四态配色 / 共享子树双环 / 根节点放大+标注 / PROBLEM_LOCAL 虚线边 / layer 分层布局
- 交互: 悬浮 tooltip、点击 reroot、右键或按钮"追溯前置"(占位回调)

## 文件结构

```
desktop/graph3d/
  package.json            # react / react-dom / three (peer) + react-force-graph-3d + typescript
  tsconfig.json           # strict + react-jsx + bundler 模块解析
  src/
    types.ts              # 与 RFC §3.4 严格一致的 TS 契约: GraphExport/SceneNode/SceneLink
    fetchGraph.ts         # fetchExport3d(): /graph/export3d 数据源, contentHash 304 语义, 契约校验
    nodePresentation.ts   # 掌握度→颜色/尺寸/光晕, 边→线型 (纯函数, 可单测)
    KnowledgeGraph3D.tsx  # 主组件
    Legend.tsx            # 图例(掌握度四态 + 共享节点双环 + 边线型)
    sampleData.ts         # LC 300 LIS 示例 GraphExport(演示共享子树高亮)
    index.ts              # 桶导出
```

## 契约对应(types.ts ↔ RFC §3.4)

| TS 类型/字段 | RFC §3.4 JSON | 说明 |
|---|---|---|
| `GraphExport.schemaVersion` | `schemaVersion` | 固定 `1`, 不符抛 `GraphContractError` |
| `GraphExport.contentHash` | `contentHash` | 增量刷新与渲染跳过的判据 |
| `GraphExport.generatedAt` | `generatedAt` | 服务端生成时间(毫秒) |
| `SceneNode.id/title` | `id/title` | learning_node id 与标题 |
| `SceneNode.layer` | `layer` | 从 root 的最长拓扑距离(服务端算好, 前端只用于分层/提示, 不重算) |
| `SceneNode.mastery` | `mastery` | `UNKNOWN/LEARNING/FRAGILE/MASTERED` |
| `SceneNode.score` | `score` | 0..1 |
| `SceneNode.parentCount` | `parentCount` | 节点大小随其增大 |
| `SceneNode.sharedByQuestions` | `sharedByQuestions` | `>=2` → 共享子树, 双环标识 |
| `SceneNode.isRoot` | `isRoot` | 根节点加大 + 文字标注 |
| `SceneLink.source/target` | `source/target` | 方向 = "source 依赖 target"(target 是前置) |
| `SceneLink.scope` | `scope` | `GLOBAL` 实线 / `PROBLEM_LOCAL` 虚线 |

REST 信封(RFC §3.2 `GraphExport{payloadJson, contentHash, nodeCount, edgeCount}`)与载荷本体两种响应
形态都被 `exportFromStatic` 兼容: 含 `payloadJson` 字段时自动解包再校验。
契约约定 `nodes`/`links` 按 `(layer, id)` 稳定排序, 本包保持原序(截断逻辑依赖该序)。

## 安装

```bash
npm i react react-dom three react-force-graph-3d
npm i -D typescript @types/react @types/react-dom @types/three
```

本包以 TS 源码形式发布(`exports` 指向 `src/index.ts`), 由 Desktop 应用的打包器
(Vite/webpack/tsc, 如 monorepo 的 `transpilePackages`)统一编译; 也可先 `npm run build` 产出 `dist/`。

## 快速开始(接 FastAPI)

```tsx
import React, { useCallback, useEffect, useState } from 'react';
import {
  KnowledgeGraph3D,
  fetchExport3d,
  type GraphExport,
  type SceneNode,
} from '@cs-learning-os/graph3d';

const API_BASE = 'http://127.0.0.1:8000';

export function GraphPage({ questionId }: { questionId: string }) {
  const [data, setData] = useState<GraphExport | null>(null);
  const [root, setRoot] = useState<{ id: string; isQuestion: boolean }>({ id: questionId, isQuestion: true });
  const [lastHash, setLastHash] = useState<string | undefined>(undefined);

  // 初次加载 + root 变化时拉取
  useEffect(() => {
    let cancelled = false;
    fetchExport3d(API_BASE, root.id, root.isQuestion)
      .then((r) => { if (!cancelled) { setData(r.data); setLastHash(r.contentHash); } })
      .catch((e) => console.error('export3d 加载失败', e));
    return () => { cancelled = true; };
  }, [root.id, root.isQuestion]);

  // reroot: 点击节点 → 以该节点为新根重新导出(RFC: reroot 是纯查询, 零写入)
  const handleNodeClick = useCallback((node: SceneNode) => {
    if (!node.isRoot) setRoot({ id: node.id, isQuestion: false });
  }, []);

  // 增量刷新: contentHash 未变时 data 引用不变, 组件跳过重渲染(见下节)
  useEffect(() => {
    const timer = setInterval(() => {
      if (!lastHash) return;
      fetchExport3d(API_BASE, root.id, root.isQuestion, lastHash)
        .then((r) => { if (r.status === 'fresh') { setData(r.data); setLastHash(r.contentHash); } })
        .catch(() => undefined);
    }, 15000);
    return () => clearInterval(timer);
  }, [root.id, root.isQuestion, lastHash]);

  if (!data) return <div>图谱加载中…</div>;
  return (
    <div style={{ width: '100%', height: '100vh' }}>
      <KnowledgeGraph3D
        data={data}
        onNodeClick={handleNodeClick}
        onTracePrerequisites={(node) => console.log('TODO 追溯前置:', node.id)}
        layout="force"
        highlightShared
      />
    </div>
  );
}
```

## contentHash 增量刷新模式(客户端侧 304 语义)

`fetchExport3d(apiBase, root, rootIsQuestion, lastHash?)` 的刷新协议:

1. 传入上次的 `contentHash` 作为 `lastHash`;
2. 若服务端实现了 ETag, 请求头带 `If-None-Match`, 服务端可直接回 `304`(省带宽);
   未实现也不影响——拿到响应后在**客户端对比 contentHash**;
3. hash 未变 → 返回 `{ status: 'not-modified', data: <缓存对象> }`, **data 引用与上次相同**;
4. hash 变了 → 校验契约、更新缓存, 返回 `{ status: 'fresh', data: <新对象> }`。

组件侧与之配合: `KnowledgeGraph3D` 的 `React.memo` 比较器只比 `data.contentHash`, 内部
`useMemo` 也以 contentHash 为键缓存力导数据——因此"未变"时整条渲染/布局管线零开销。
服务端契约稳定性(导出同内容必同 hash)由 RFC §5 的"导出 JSON 契约稳定(contentHash)"测试保证。

错误处理: HTTP 非 2xx → `GraphFetchError`(含 `status`); 响应不合 RFC §3.4 → `GraphContractError`。

## reroot 交互流程(文字流程图)

```
用户点击节点 N
   │
   ▼
KnowledgeGraph3D.onNodeClick(N)  ── N.isRoot? 建议调用方忽略(已是当前根)
   │
   ▼
调用方请求新根数据(二选一):
  ① 推荐: GET /graph/export3d?root={N.id}&rootIsQuestion=false
          → 直接得到 GraphExport, fetchExport3d(API_BASE, N.id, false) 即可
  ② GET /graph/nodes/{N.id}/subtree (RFC subtreeOf, 返回 TreeSnapshot)
          → 需自行映射为 GraphExport: KGNodeDto{nodeId,title,depth,parentCount,mastery}
            中 depth≈layer; 注意 TreeSnapshot 没有 score/sharedByQuestions,
            映射时 score 置 0、sharedByQuestions 按 sharedNodeIds 置 1/2,
            视觉信息会比 ① 少, 仅在没有 export3d 可用时作为降级方案
   │
   ▼
setData(新 GraphExport)
   │
   ▼
contentHash 变化 → 组件重建力导数据并重新布局; 相机自动 zoomToFit
(RFC §3.1: reroot 是纯查询, 零写入, 任意节点可当根)
```

## "追溯前置"占位回调

`onTracePrerequisites?: (node) => void` 在以下两种时机触发:
右键节点; 或先左键选中节点、再点左下角面板的"追溯前置"按钮(未传回调时按钮置灰)。

本包只负责交互入口; 业务侧可在此对接 `POST /graph/proposals/prerequisite-chain`
(AI 前置链建议, 经 Proposal→Confirm 落库, RFC §3.6)或 `GET /graph/quizzes/{qid}/gap`(瓶颈诊断)。

## 更换数据源

1. **FastAPI(默认)**: `fetchExport3d('http://127.0.0.1:8000', root, rootIsQuestion, lastHash?)`。
2. **本地静态 JSON(离线开发)**: `apiBase` 直接传以 `.json` 结尾的 URL,
   如 `fetchExport3d('/mocks/lis.json', '', false)` —— 命中静态文件分支, 原样 GET 后走同一套契约校验与缓存。
3. **内存对象(单测/Demo)**: `exportFromStatic(rawObject)` 校验并返回 `GraphExport`;
   或直接使用内置示例 `SAMPLE_LIS_EXPORT`(LC 300 LIS 问题树, 含共享子树与 PROBLEM_LOCAL 虚线边):

```tsx
import { KnowledgeGraph3D, SAMPLE_LIS_EXPORT } from '@cs-learning-os/graph3d';

<KnowledgeGraph3D data={SAMPLE_LIS_EXPORT} />
```

自定义数据源只要最终产出符合 RFC §3.4 的 `GraphExport` 即可, 组件不感知来源。

## 视觉编码规范(见 nodePresentation.ts, 单点可改)

| 维度 | 编码 |
|---|---|
| 背景 | 暖白 `#faf6ee`, 低饱和暖色板, 无蓝紫渐变 |
| UNKNOWN | 灰 `#a5a29a`, 无光晕 |
| LEARNING | 琥珀 `#d2a24c`, 弱光晕 |
| FRAGILE | 红 `#c05f4e`, 强光晕(最醒目) |
| MASTERED | 绿 `#6f9f72`, 微光晕 |
| 节点尺寸 | `3.2 × (1 + 0.3·log2(1+parentCount))`, 根 ×2.0, 共享 ×1.15 |
| 共享节点 | `sharedByQuestions>=2` → 暖铜色双环(`highlightShared={false}` 可关) |
| 根节点 | 加大 + "ROOT · 标题" 文字标注 sprite |
| GLOBAL 边 | 暖灰实线 |
| PROBLEM_LOCAL 边 | 暖赭虚线(three `LineDashedMaterial`, 每帧重算 lineDistances) |
| 边方向 | 小箭头指向前置(target) |

## 布局(layout prop)

- `force`(默认): 自由 3D 力导布局; `layer` 仅用于 tooltip/截断, 不参与定位。
- `layered`: 用锚点坐标 `fz = layer × LAYER_Z_SPACING` 把节点钉在所属 layer 的 z 平面上,
  x/y 仍由力导舒展, 相机自动转到侧视角。
  实现上刻意**不用 `dagMode`**: `layer` 是服务端算好的最长拓扑距离, 直接锚定完全贴合契约,
  而 dagMode 会在前端按边重新推导层级, 可能与服务端不一致。
  右上角工具栏可在两种布局间本地切换(prop 变化时同步回受控值)。

## 性能设计

- `React.memo` 自定义比较器: `data` 只比 `contentHash`, 内容未变即使引用变了也跳过重渲染;
- 组件内部以 contentHash 为键缓存力导 `graphData`(useMemo + ref), 引用稳定 → 力导布局不重置、相机不跳;
- 从契约数据到力导数据必经浅拷贝(引擎会原地注入 x/y/z、把 link 端点换成对象引用);
- 球体几何体/材质/光环纹理/圆环按特征做模块级缓存, 不为每个节点重复分配 GPU 资源;
- 边全部走自定义 `linkThreeObject`(实线/虚线两套共享材质), 每帧只更新 position buffer;
- 节点数 > `maxNodes`(默认 1500)时按契约 `(layer, id)` 序保留最浅层截断, 顶部横幅提示
  "显示 1500 / N 个节点", 并自动剔除悬空边。

## API 摘要

### `<KnowledgeGraph3D />`

| Prop | 类型 | 默认 | 说明 |
|---|---|---|---|
| `data` | `GraphExport` | 必填 | RFC §3.4 导出数据 |
| `onNodeClick` | `(node: SceneNode) => void` | — | 点击节点(reroot 入口, 见流程图) |
| `onTracePrerequisites` | `(node: SceneNode) => void` | — | "追溯前置"占位回调(右键/面板按钮) |
| `highlightShared` | `boolean` | `true` | 共享子树双环高亮开关 |
| `layout` | `'force' \| 'layered'` | `'force'` | 布局模式 |
| `maxNodes` | `number` | `1500` | 节点数上限, 超出截断并提示 |
| `showLegend` | `boolean` | `true` | 是否显示图例 |
| `width` / `height` | `number` | — | 固定尺寸; 缺省用 ResizeObserver 跟随容器 |
| `className` / `style` | — | — | 外层容器样式 |

悬浮 tooltip 内容: 标题、掌握度(中文+得分)、layer、parentCount、sharedByQuestions、根/共享标识。

### `fetchGraph.ts`

| 导出 | 说明 |
|---|---|
| `fetchExport3d(apiBase, root, rootIsQuestion, lastHash?, options?)` | 返回 `Promise<{ data, status: 'fresh'\|'not-modified', contentHash }>`; `options`: `signal/fetchImpl/extraHeaders` |
| `exportFromStatic(raw)` | 校验任意未知数据 → `GraphExport`, 兼容 REST 信封; 不合规抛 `GraphContractError` |
| `export3dUrl(apiBase, root, rootIsQuestion)` | 请求 URL 构造(`.json` 结尾视为静态文件) |
| `clearExportCache(key?)` | 清空 contentHash 缓存 |
| `GraphFetchError` / `GraphContractError` | HTTP 层错误 / 契约校验错误 |

### `nodePresentation.ts`(纯函数)

`nodeColor(node)` / `nodeSize(node)` / `nodeGlow(node)` / `linkStyle(link)` / `isSharedNode(node)` /
`truncateGraph(data, maxNodes)` / `masteryVisual(mastery)` / `masteryLabel` / `formatScore` / `shortHash`,
常量 `MASTERY_VISUALS`、`GRAPH_THEME`、`LAYER_Z_SPACING` 等。契约或设计变更时单点修改本文件即可。

### 其他

`Legend`(图例)、`SAMPLE_LIS_EXPORT`(LC 300 LIS 示例)、全部契约类型(`GraphExport/SceneNode/SceneLink/
MasteryState/EdgeScope/RuntimeNode/RuntimeLink/GraphExportEnvelope`)。

## 嵌入现有 Desktop(React)应用

1. monorepo: `desktop/app` 的 package.json 加 `"@cs-learning-os/graph3d": "workspace:*"`(或 file: 依赖),
   并在打包器配置中把本包加入转译列表(Vite 默认处理 ESM 源码即可; webpack 需让 babel/ts-loader 覆盖本包);
2. 给一个**有确定高度**的容器(如 `height: 100vh` 或 flex 拉伸), 组件用 ResizeObserver 自适应;
   也可直接传 `width`/`height` 固定尺寸;
3. WebGL 依赖: Electron/桌面 Chromium 开箱即用; 首屏渲染 1500 节点级别无需额外调优;
4. 类型检查: 本包内 `npm run typecheck`(strict 模式, 不产出文件)。
