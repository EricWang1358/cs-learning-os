/**
 * 节点/边的视觉编码纯函数: 掌握度 → 颜色/尺寸/光晕, 边作用域 → 线型。
 *
 * 全部为无副作用纯函数, 可独立单测; 后端契约(RFC §3.4)或设计规范变更时
 * 只需单点修改本文件, 组件与图例均从这里取色。
 *
 * 设计约束: 低饱和度暖色系色板 + 暖白背景, 不使用蓝紫渐变。
 */

import type { EdgeScope, GraphExport, MasteryState, SceneLink, SceneNode } from './types';

/** 单个掌握度状态的完整视觉描述 */
export interface MasteryVisual {
  /** 节点底色 (低饱和度) */
  color: string;
  /** 自发光颜色 (three 材质 emissive) */
  emissive: string;
  /** 自发光强度 0..1, 越大光晕感越强 */
  emissiveIntensity: number;
  /** 外层光晕 sprite 相对节点半径的放大倍数; 0 = 无光晕 */
  haloScale: number;
  /** 图例中文名 */
  label: string;
  /** 图例说明文案 */
  description: string;
}

/** 掌握度四态 → 视觉映射 (UNKNOWN 灰 / LEARNING 琥珀 / FRAGILE 红 / MASTERED 绿) */
export const MASTERY_VISUALS: Record<MasteryState, MasteryVisual> = {
  UNKNOWN: {
    color: '#a5a29a',
    emissive: '#000000',
    emissiveIntensity: 0,
    haloScale: 0,
    label: '未知',
    description: '尚无测验证据',
  },
  LEARNING: {
    color: '#d2a24c',
    emissive: '#8a6420',
    emissiveIntensity: 0.25,
    haloScale: 1.35,
    label: '学习中',
    description: '已有作答, 尚未稳定',
  },
  FRAGILE: {
    color: '#c05f4e',
    emissive: '#7e2f22',
    emissiveIntensity: 0.55,
    haloScale: 1.9,
    label: '脆弱',
    description: '近期答错, 优先复习',
  },
  MASTERED: {
    color: '#6f9f72',
    emissive: '#33572f',
    emissiveIntensity: 0.3,
    haloScale: 1.25,
    label: '已掌握',
    description: '得分 ≥0.8 且近期无失误',
  },
};

/** 全局主题(暖白底 + 低饱和中性色), 组件/图例/悬浮层共用 */
export const GRAPH_THEME = {
  background: '#faf6ee',
  panelBackground: 'rgba(255, 252, 245, 0.94)',
  panelBorder: '#e4dcc8',
  textPrimary: '#3c382f',
  textSecondary: '#837a68',
  /** GLOBAL 实线边颜色 (暖灰) */
  linkGlobalColor: '#a89e8b',
  /** PROBLEM_LOCAL 虚线边颜色 (暖赭) */
  linkLocalColor: '#c58a5a',
  linkOpacity: 0.55,
  /** 共享子树双环颜色 (暖铜) */
  sharedRingColor: '#8f6b3d',
} as const;

/** 节点基础半径(世界坐标单位) */
export const NODE_BASE_RADIUS = 3.2;
/** 根节点放大倍数 */
export const ROOT_SIZE_FACTOR = 2.0;
/** 共享节点放大倍数 */
export const SHARED_SIZE_FACTOR = 1.15;
/** layered 布局下相邻 layer 的 z 轴间距 */
export const LAYER_Z_SPACING = 42;

/** 掌握度 → 视觉描述 */
export function masteryVisual(mastery: MasteryState): MasteryVisual {
  return MASTERY_VISUALS[mastery];
}

/** 是否共享子树节点 (RFC: sharedByQuestions >= 2 即被多棵问题树复用) */
export function isSharedNode(node: Pick<SceneNode, 'sharedByQuestions'>): boolean {
  return node.sharedByQuestions >= 2;
}

/** 节点底色 = 掌握度底色 (根/共享通过尺寸与描边表达, 不改色相) */
export function nodeColor(node: Pick<SceneNode, 'mastery'>): string {
  return MASTERY_VISUALS[node.mastery].color;
}

/** 节点光晕参数; 根节点在所属掌握度基础上略增强 */
export interface NodeGlow {
  emissive: string;
  emissiveIntensity: number;
  haloScale: number;
}

export function nodeGlow(node: Pick<SceneNode, 'mastery' | 'isRoot'>): NodeGlow {
  const visual = MASTERY_VISUALS[node.mastery];
  if (!node.isRoot) {
    return {
      emissive: visual.emissive,
      emissiveIntensity: visual.emissiveIntensity,
      haloScale: visual.haloScale,
    };
  }
  return {
    emissive: visual.emissive,
    emissiveIntensity: Math.min(1, visual.emissiveIntensity + 0.15),
    haloScale: visual.haloScale > 0 ? visual.haloScale * 1.25 : 1.4,
  };
}

/**
 * 节点半径: 随 parentCount 对数增长(被依赖越多越大), 根与共享节点再放大。
 * 返回值保留两位小数以便几何体缓存命中。
 */
export function nodeSize(node: Pick<SceneNode, 'parentCount' | 'isRoot' | 'sharedByQuestions'>): number {
  const safeParentCount = Math.max(0, node.parentCount);
  let radius = NODE_BASE_RADIUS * (1 + 0.3 * Math.log2(1 + safeParentCount));
  if (node.isRoot) radius *= ROOT_SIZE_FACTOR;
  if (isSharedNode(node)) radius *= SHARED_SIZE_FACTOR;
  return Math.round(radius * 100) / 100;
}

/** 边线型描述: GLOBAL 实线 / PROBLEM_LOCAL 虚线 (three LineDashedMaterial) */
export interface LinkStyle {
  color: string;
  dashed: boolean;
  /** 虚线段长(世界单位), 实线为 0 */
  dashSize: number;
  /** 虚线间隔长, 实线为 0 */
  gapSize: number;
  opacity: number;
  width: number;
}

export function linkStyle(link: Pick<SceneLink, 'scope'>): LinkStyle {
  if (link.scope === 'PROBLEM_LOCAL') {
    return {
      color: GRAPH_THEME.linkLocalColor,
      dashed: true,
      dashSize: 4,
      gapSize: 2.5,
      opacity: 0.9,
      width: 1.6,
    };
  }
  return {
    color: GRAPH_THEME.linkGlobalColor,
    dashed: false,
    dashSize: 0,
    gapSize: 0,
    opacity: GRAPH_THEME.linkOpacity,
    width: 1,
  };
}

/** 截断结果: 图数据 + 是否发生了截断 + 原始节点总数 */
export interface TruncateResult {
  graph: GraphExport;
  truncated: boolean;
  totalNodes: number;
}

/**
 * 按契约的 `(layer, id)` 稳定排序直接截断前 maxNodes 个节点(保留最浅层),
 * 并剔除端点已不在保留集内的边。纯函数, 不修改入参。
 */
export function truncateGraph(data: GraphExport, maxNodes: number): TruncateResult {
  if (maxNodes <= 0 || data.nodes.length <= maxNodes) {
    return { graph: data, truncated: false, totalNodes: data.nodes.length };
  }
  const keptNodes = data.nodes.slice(0, maxNodes);
  const keptIds = new Set(keptNodes.map((n) => n.id));
  const keptLinks = data.links.filter((l) => keptIds.has(l.source) && keptIds.has(l.target));
  return {
    graph: { ...data, nodes: keptNodes, links: keptLinks },
    truncated: true,
    totalNodes: data.nodes.length,
  };
}

/** 掌握度 → 中文名 (tooltip / 面板用) */
export function masteryLabel(mastery: MasteryState): string {
  return MASTERY_VISUALS[mastery].label;
}

/** 得分 0..1 → 百分比文案, 越界值收敛到 [0,1] */
export function formatScore(score: number): string {
  const clamped = score < 0 ? 0 : score > 1 ? 1 : score;
  return `${Math.round(clamped * 100)}%`;
}

/** contentHash 缩短展示(去掉 sha256: 等前缀后取前若干位) */
export function shortHash(contentHash: string, head = 10): string {
  const bare = contentHash.includes(':') ? contentHash.slice(contentHash.lastIndexOf(':') + 1) : contentHash;
  return bare.length <= head ? bare : bare.slice(0, head);
}

/** 所有边作用域的线型样例(图例用, 保证与 linkStyle 同源) */
export function linkStyleSamples(): Array<{ scope: EdgeScope; style: LinkStyle }> {
  return [
    { scope: 'GLOBAL', style: linkStyle({ scope: 'GLOBAL' }) },
    { scope: 'PROBLEM_LOCAL', style: linkStyle({ scope: 'PROBLEM_LOCAL' }) },
  ];
}
