/**
 * @cs-learning-os/graph3d 桶导出。
 * 契约类型见 types.ts (RFC §3.4); 视觉映射见 nodePresentation.ts; 数据源见 fetchGraph.ts。
 */

// ---- 契约类型 (RFC §3.4 / §3.2) ----
export type {
  MasteryState,
  EdgeScope,
  SceneNode,
  SceneLink,
  GraphExport,
  GraphExportEnvelope,
  RuntimeNode,
  RuntimeLink,
} from './types';
export { GRAPH_EXPORT_SCHEMA_VERSION, MASTERY_STATES, EDGE_SCOPES } from './types';

// ---- 数据源 (RFC §3.6 GET /graph/export3d) ----
export { fetchExport3d, exportFromStatic, export3dUrl, clearExportCache, GraphContractError, GraphFetchError } from './fetchGraph';
export type { FetchExportOptions, FetchExportResult, FetchStatus } from './fetchGraph';

// ---- 视觉映射纯函数 ----
export {
  MASTERY_VISUALS,
  GRAPH_THEME,
  NODE_BASE_RADIUS,
  ROOT_SIZE_FACTOR,
  SHARED_SIZE_FACTOR,
  LAYER_Z_SPACING,
  masteryVisual,
  isSharedNode,
  nodeColor,
  nodeGlow,
  nodeSize,
  linkStyle,
  linkStyleSamples,
  truncateGraph,
  masteryLabel,
  formatScore,
  shortHash,
} from './nodePresentation';
export type { MasteryVisual, NodeGlow, LinkStyle, TruncateResult } from './nodePresentation';

// ---- 组件 ----
export { KnowledgeGraph3D } from './KnowledgeGraph3D';
export type { KnowledgeGraph3DProps, GraphLayoutMode } from './KnowledgeGraph3D';
export { Legend } from './Legend';
export type { LegendProps } from './Legend';

// ---- 离线示例数据 ----
export { SAMPLE_LIS_EXPORT } from './sampleData';
