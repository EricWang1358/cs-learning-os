/**
 * 知识图谱 3D 导出契约类型。
 *
 * 与 docs/RFC-knowledge-graph.md §3.4 (schemaVersion=1) 严格一致:
 * 后端 `GraphExport.payloadJson` 反序列化后即为本文件的 {@link GraphExport}。
 * §3.2 的 REST 信封见 {@link GraphExportEnvelope}。
 *
 * 后端契约变更时只需同步修改本文件与 nodePresentation.ts。
 */

/** 导出契约版本号 (RFC §3.4 固定为 1) */
export const GRAPH_EXPORT_SCHEMA_VERSION = 1 as const;

/** 掌握度四态, 对应 RFC §3.2 `MasteryState` */
export type MasteryState = 'UNKNOWN' | 'LEARNING' | 'FRAGILE' | 'MASTERED';

/** 全部掌握度取值(按图例展示顺序), 供校验与遍历 */
export const MASTERY_STATES: readonly MasteryState[] = ['UNKNOWN', 'LEARNING', 'FRAGILE', 'MASTERED'];

/** 边作用域, 对应 RFC §3.2 `EdgeScope` */
export type EdgeScope = 'GLOBAL' | 'PROBLEM_LOCAL';

/** 全部边作用域取值, 供校验 */
export const EDGE_SCOPES: readonly EdgeScope[] = ['GLOBAL', 'PROBLEM_LOCAL'];

/**
 * 3D 场景节点 (RFC §3.4 `nodes[]` 元素)。
 *
 * `layer` 为服务端算好的"从 root 的最长拓扑距离", 前端只用于着色/分层, 不重算。
 */
export interface SceneNode {
  /** learning_node id */
  id: string;
  /** 节点标题 */
  title: string;
  /** 从 root 的最长拓扑距离 (服务端计算) */
  layer: number;
  /** 掌握度四态 */
  mastery: MasteryState;
  /** 掌握度得分, 0..1 */
  score: number;
  /** 父节点数 (依赖该节点的上游数量, 共享度信号之一) */
  parentCount: number;
  /** 被多少棵不同问题树引用; >= 2 即共享子树(双环标识) */
  sharedByQuestions: number;
  /** 是否为本次导出的根(问题根或 reroot 节点) */
  isRoot: boolean;
}

/**
 * 3D 场景边 (RFC §3.4 `links[]` 元素)。
 * 方向语义与全局 DAG 一致: `source → target` 表示 "source 依赖 target" (target 是前置知识)。
 */
export interface SceneLink {
  /** 父节点 id (依赖方) */
  source: string;
  /** 子节点 id (前置方) */
  target: string;
  /** GLOBAL=全局共享边(实线); PROBLEM_LOCAL=某问题私有边(虚线) */
  scope: EdgeScope;
}

/**
 * 3D 导出载荷 (RFC §3.4, schemaVersion=1)。
 * `nodes`/`links` 已由服务端按 `(layer, id)` 稳定排序, 前端保持原序。
 */
export interface GraphExport {
  schemaVersion: typeof GRAPH_EXPORT_SCHEMA_VERSION;
  /** 内容哈希(如 sha256), 增量刷新与渲染跳过的判据 */
  contentHash: string;
  /** 服务端生成时间戳(毫秒) */
  generatedAt: number;
  nodes: SceneNode[];
  links: SceneLink[];
}

/**
 * REST 信封 (RFC §3.2 `GraphExport` DTO):
 * `GET /graph/export3d` 的响应体可能是本信封(`payloadJson` 为序列化后的 {@link GraphExport}),
 * 也可能直接返回载荷本体; fetchGraph.ts 两种都兼容。
 */
export interface GraphExportEnvelope {
  payloadJson: string;
  contentHash: string;
  nodeCount: number;
  edgeCount: number;
}

/**
 * 运行时节点: 契约字段 + d3-force-3d 引擎注入的坐标/速度/锚点字段。
 * 引擎会原地修改节点对象, 故从契约数据到运行时数据必须拷贝(见 KnowledgeGraph3D)。
 */
export type RuntimeNode = SceneNode & {
  x?: number;
  y?: number;
  z?: number;
  vx?: number;
  vy?: number;
  vz?: number;
  /** 锚定坐标(layered 布局用 fz 把节点钉在所属 layer 平面) */
  fx?: number;
  fy?: number;
  fz?: number;
  /** 兼容力导库注入的其他字段 */
  [extra: string]: unknown;
};

/**
 * 运行时边: 引擎初始化后 `source`/`target` 会被替换为节点对象引用, 故为联合类型。
 * 契约侧逻辑(过滤/截断)请在交给力导库之前完成。
 */
export type RuntimeLink = Omit<SceneLink, 'source' | 'target'> & {
  source: string | number | RuntimeNode;
  target: string | number | RuntimeNode;
  [extra: string]: unknown;
};
