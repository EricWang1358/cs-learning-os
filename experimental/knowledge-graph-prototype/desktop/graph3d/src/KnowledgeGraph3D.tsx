/**
 * KnowledgeGraph3D — Desktop 端 3D 知识图谱主组件 (react-force-graph-3d 渲染)。
 *
 * 视觉编码(全部经 nodePresentation 纯函数映射):
 * - 掌握度四态: UNKNOWN 灰 / LEARNING 琥珀 / FRAGILE 红 / MASTERED 绿 (低饱和暖色板 + 暖白底);
 * - 节点尺寸随 parentCount 对数增大; 根节点加大并带文字标注;
 * - 共享节点(sharedByQuestions >= 2)加双环描边, 可用 highlightShared 关闭;
 * - PROBLEM_LOCAL 边为虚线(three LineDashedMaterial), GLOBAL 边为实线;
 * - 光晕(halo sprite + emissive)随掌握度变化, FRAGILE 最强。
 *
 * 性能: contentHash 不变则 graphData 引用不变(useMemo + 模块级缓存 + React.memo 比较器),
 * 节点数超过 maxNodes(默认 1500)按契约 (layer, id) 序截断并给出提示。
 */

import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import * as THREE from 'three';
import ForceGraph3D from 'react-force-graph-3d';
import type { ForceGraphMethods, GraphData, LinkObject, NodeObject } from 'react-force-graph-3d';
import type { EdgeScope, GraphExport, RuntimeLink, RuntimeNode, SceneNode } from './types';
import {
  GRAPH_THEME,
  LAYER_Z_SPACING,
  formatScore,
  isSharedNode,
  linkStyle,
  masteryVisual,
  nodeColor,
  nodeGlow,
  nodeSize,
  shortHash,
  truncateGraph,
} from './nodePresentation';
import { Legend } from './Legend';

/** 布局模式: force=自由力导; layered=按 layer 锚定 z 轴分层 */
export type GraphLayoutMode = 'force' | 'layered';

export interface KnowledgeGraph3DProps {
  /** RFC §3.4 导出数据; contentHash 不变时组件跳过重渲染与重布局 */
  data: GraphExport;
  /**
   * 点击节点(reroot): 调用方应请求新根的导出数据并替换 data。
   * 推荐 `GET /graph/export3d?root={node.id}&rootIsQuestion=false` (见 fetchGraph.fetchExport3d);
   * 也可 `GET /graph/nodes/{id}/subtree` 后自行映射为 GraphExport。
   */
  onNodeClick?: (node: SceneNode) => void;
  /** 共享子树(sharedByQuestions >= 2)双环高亮开关, 默认 true */
  highlightShared?: boolean;
  /** 布局: 'force' 自由力导(默认) | 'layered' 按 layer 分层(z 轴) */
  layout?: GraphLayoutMode;
  /** "追溯前置"占位回调: 右键节点, 或选中节点后点击左下面板按钮触发 */
  onTracePrerequisites?: (node: SceneNode) => void;
  /** 节点数上限, 超出按 (layer, id) 截断并提示, 默认 1500 */
  maxNodes?: number;
  /** 是否显示图例, 默认 true */
  showLegend?: boolean;
  /** 固定宽度(px); 与 height 同时缺省时跟随容器(ResizeObserver) */
  width?: number;
  /** 固定高度(px) */
  height?: number;
  className?: string;
  style?: React.CSSProperties;
}

const DEFAULT_MAX_NODES = 1500;
const FALLBACK_WIDTH = 960;
const FALLBACK_HEIGHT = 600;

/** 力导库视角下的节点/边对象类型(契约字段 + 引擎注入字段) */
type GraphNodeObject = NodeObject<RuntimeNode>;
type GraphLinkObject = LinkObject<RuntimeNode, RuntimeLink>;
type GraphHandle = ForceGraphMethods<GraphNodeObject, GraphLinkObject>;
type Vec3 = { x: number; y: number; z: number };

// ---------------------------------------------------------------------------
// three.js 对象构建(几何体/材质按特征缓存, 避免每节点重复分配)
// ---------------------------------------------------------------------------

const sphereGeometryCache = new Map<number, THREE.SphereGeometry>();
const nodeMaterialCache = new Map<string, THREE.MeshLambertMaterial>();
const ringGeometryCache = new Map<string, THREE.TorusGeometry>();
const haloTextureCache = new Map<string, THREE.CanvasTexture>();
const lineMaterialCache = new Map<EdgeScope, THREE.Material>();
let ringMaterialSingleton: THREE.MeshBasicMaterial | null = null;

function sphereGeometryOf(radius: number): THREE.SphereGeometry {
  let geometry = sphereGeometryCache.get(radius);
  if (!geometry) {
    geometry = new THREE.SphereGeometry(radius, 20, 16);
    sphereGeometryCache.set(radius, geometry);
  }
  return geometry;
}

function nodeMaterialOf(color: string, emissive: string, emissiveIntensity: number): THREE.MeshLambertMaterial {
  const key = `${color}|${emissive}|${emissiveIntensity}`;
  let material = nodeMaterialCache.get(key);
  if (!material) {
    material = new THREE.MeshLambertMaterial({ color, emissive, emissiveIntensity });
    nodeMaterialCache.set(key, material);
  }
  return material;
}

function ringGeometryOf(radius: number, tube: number): THREE.TorusGeometry {
  const key = `${radius}|${tube}`;
  let geometry = ringGeometryCache.get(key);
  if (!geometry) {
    geometry = new THREE.TorusGeometry(radius, tube, 8, 40);
    ringGeometryCache.set(key, geometry);
  }
  return geometry;
}

function ringMaterialOf(): THREE.MeshBasicMaterial {
  if (!ringMaterialSingleton) {
    ringMaterialSingleton = new THREE.MeshBasicMaterial({ color: GRAPH_THEME.sharedRingColor });
  }
  return ringMaterialSingleton;
}

/** 六边形/颜色字符串 → rgba() (Canvas 渐变用) */
function colorWithAlpha(color: string, alpha: number): string {
  const parsed = new THREE.Color(color);
  const r = Math.round(parsed.r * 255);
  const g = Math.round(parsed.g * 255);
  const b = Math.round(parsed.b * 255);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

function haloTextureOf(color: string): THREE.CanvasTexture {
  const cached = haloTextureCache.get(color);
  if (cached) return cached;
  const size = 128;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');
  if (ctx) {
    const gradient = ctx.createRadialGradient(size / 2, size / 2, size * 0.08, size / 2, size / 2, size / 2);
    gradient.addColorStop(0, colorWithAlpha(color, 0.75));
    gradient.addColorStop(0.55, colorWithAlpha(color, 0.22));
    gradient.addColorStop(1, colorWithAlpha(color, 0));
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, size, size);
  }
  const texture = new THREE.CanvasTexture(canvas);
  haloTextureCache.set(color, texture);
  return texture;
}

function roundedRectPath(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  radius: number,
): void {
  ctx.beginPath();
  ctx.moveTo(x + radius, y);
  ctx.arcTo(x + width, y, x + width, y + height, radius);
  ctx.arcTo(x + width, y + height, x, y + height, radius);
  ctx.arcTo(x, y + height, x, y, radius);
  ctx.arcTo(x, y, x + width, y, radius);
  ctx.closePath();
}

/** 根节点文字标注 sprite ("ROOT · 标题") */
function makeRootLabel(title: string): THREE.Sprite {
  const text = `ROOT · ${title}`;
  const font = '600 26px "PingFang SC", "Microsoft YaHei", system-ui, sans-serif';
  const canvas = document.createElement('canvas');
  const ctx = canvas.getContext('2d');
  let texture: THREE.CanvasTexture | null = null;
  if (ctx) {
    ctx.font = font;
    const padX = 18;
    const width = Math.ceil(ctx.measureText(text).width) + padX * 2;
    const height = 46;
    canvas.width = width;
    canvas.height = height;
    // 修改 canvas 尺寸会重置上下文状态, 需重设 font
    ctx.font = font;
    ctx.fillStyle = GRAPH_THEME.panelBackground;
    roundedRectPath(ctx, 1, 1, width - 2, height - 2, 10);
    ctx.fill();
    ctx.strokeStyle = GRAPH_THEME.panelBorder;
    ctx.stroke();
    ctx.fillStyle = GRAPH_THEME.textPrimary;
    ctx.textBaseline = 'middle';
    ctx.fillText(text, padX, height / 2 + 1);
    texture = new THREE.CanvasTexture(canvas);
  }
  const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: texture, transparent: true, depthWrite: false }));
  const aspect = canvas.width / Math.max(1, canvas.height);
  const worldHeight = 9;
  sprite.scale.set(worldHeight * aspect, worldHeight, 1);
  return sprite;
}

/** 构建单个节点的 three 对象: 光晕 sprite + 球体 + (共享)双环 + (根)标注 */
function buildNodeObject(node: GraphNodeObject, highlightShared: boolean): THREE.Object3D {
  const group = new THREE.Group();
  const radius = nodeSize(node);
  const glow = nodeGlow(node);

  if (glow.haloScale > 0) {
    const halo = new THREE.Sprite(
      new THREE.SpriteMaterial({
        map: haloTextureOf(nodeColor(node)),
        transparent: true,
        opacity: 0.55,
        depthWrite: false,
      }),
    );
    const haloSize = radius * glow.haloScale * 2.2;
    halo.scale.set(haloSize, haloSize, 1);
    group.add(halo);
  }

  const sphere = new THREE.Mesh(
    sphereGeometryOf(radius),
    nodeMaterialOf(nodeColor(node), glow.emissive, glow.emissiveIntensity),
  );
  group.add(sphere);

  if (highlightShared && isSharedNode(node)) {
    const inner = new THREE.Mesh(ringGeometryOf(radius * 1.45, Math.max(0.32, radius * 0.07)), ringMaterialOf());
    const outer = new THREE.Mesh(ringGeometryOf(radius * 1.78, Math.max(0.26, radius * 0.055)), ringMaterialOf());
    group.add(inner, outer);
  }

  if (node.isRoot) {
    const label = makeRootLabel(node.title);
    label.position.set(0, radius * 2.2 + 6, 0);
    group.add(label);
  }

  return group;
}

function lineMaterialOf(scope: EdgeScope): THREE.Material {
  const cached = lineMaterialCache.get(scope);
  if (cached) return cached;
  const style = linkStyle({ scope });
  const material = style.dashed
    ? new THREE.LineDashedMaterial({
        color: style.color,
        dashSize: style.dashSize,
        gapSize: style.gapSize,
        transparent: true,
        opacity: style.opacity,
        linewidth: style.width,
      })
    : new THREE.LineBasicMaterial({
        color: style.color,
        transparent: true,
        opacity: style.opacity,
        linewidth: style.width,
      });
  lineMaterialCache.set(scope, material);
  return material;
}

/** 构建单条边的 three 对象: GLOBAL 实线 / PROBLEM_LOCAL 虚线(LineDashedMaterial) */
function buildLinkObject(link: GraphLinkObject): THREE.Object3D {
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.BufferAttribute(new Float32Array(2 * 3), 3));
  const line = new THREE.Line(geometry, lineMaterialOf(link.scope));
  // 端点每帧更新, 关闭视锥剔除避免包围球过期导致线消失
  line.frustumCulled = false;
  return line;
}

/** 自定义边定位: 更新端点; 虚线材质必须重算 lineDistances */
function updateLinkPosition(obj: THREE.Object3D, coords: { start: Vec3; end: Vec3 }): boolean {
  const line = obj as THREE.Line;
  const attribute = line.geometry.getAttribute('position') as THREE.BufferAttribute | undefined;
  if (!attribute) return false;
  attribute.setXYZ(0, coords.start.x, coords.start.y, coords.start.z);
  attribute.setXYZ(1, coords.end.x, coords.end.y, coords.end.z);
  attribute.needsUpdate = true;
  const material = line.material as THREE.Material & { isLineDashedMaterial?: boolean };
  if (material.isLineDashedMaterial) {
    line.computeLineDistances();
  }
  return true;
}

// ---------------------------------------------------------------------------
// tooltip
// ---------------------------------------------------------------------------

const HTML_ESCAPES: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
};

function escapeHtml(text: string): string {
  return text.replace(/[&<>"']/g, (ch) => HTML_ESCAPES[ch] ?? ch);
}

function tooltipOf(node: GraphNodeObject): string {
  const visual = masteryVisual(node.mastery);
  const lines = [
    `<div style="font-weight:600;font-size:13px;margin-bottom:4px">${escapeHtml(node.title)}</div>`,
    `<div>掌握度: ${visual.label} · 得分 ${formatScore(node.score)}</div>`,
    `<div>layer ${node.layer} · parentCount ${node.parentCount} · 被 ${node.sharedByQuestions} 棵问题树引用</div>`,
  ];
  if (node.isRoot) lines.push('<div style="margin-top:2px">当前根节点(加大 + 标注)</div>');
  if (isSharedNode(node)) lines.push('<div style="margin-top:2px">共享子树(双环标识)</div>');
  return `<div style="text-align:left;line-height:1.5">${lines.join('')}</div>`;
}

// ---------------------------------------------------------------------------
// 悬浮层样式
// ---------------------------------------------------------------------------

const overlayFont = '"PingFang SC", "Microsoft YaHei", system-ui, sans-serif';

const toolbarStyle: React.CSSProperties = {
  position: 'absolute',
  top: 12,
  right: 12,
  display: 'flex',
  gap: 6,
  zIndex: 2,
};

const toolButtonStyle: React.CSSProperties = {
  border: `1px solid ${GRAPH_THEME.panelBorder}`,
  background: GRAPH_THEME.panelBackground,
  color: GRAPH_THEME.textPrimary,
  borderRadius: 8,
  padding: '4px 10px',
  fontSize: 12,
  cursor: 'pointer',
  fontFamily: overlayFont,
};

const legendDockStyle: React.CSSProperties = {
  position: 'absolute',
  top: 12,
  left: 12,
  zIndex: 2,
  maxWidth: 260,
};

const panelStyle: React.CSSProperties = {
  position: 'absolute',
  left: 12,
  bottom: 12,
  zIndex: 2,
  maxWidth: 320,
  padding: '10px 12px',
  background: GRAPH_THEME.panelBackground,
  border: `1px solid ${GRAPH_THEME.panelBorder}`,
  borderRadius: 10,
  fontSize: 12,
  lineHeight: 1.5,
  color: GRAPH_THEME.textPrimary,
  fontFamily: overlayFont,
  boxShadow: '0 2px 10px rgba(90, 80, 60, 0.08)',
};

const statusBarStyle: React.CSSProperties = {
  position: 'absolute',
  right: 12,
  bottom: 12,
  zIndex: 2,
  padding: '4px 10px',
  background: GRAPH_THEME.panelBackground,
  border: `1px solid ${GRAPH_THEME.panelBorder}`,
  borderRadius: 8,
  fontSize: 11,
  color: GRAPH_THEME.textSecondary,
  fontFamily: overlayFont,
};

const truncBannerStyle: React.CSSProperties = {
  position: 'absolute',
  top: 12,
  left: '50%',
  transform: 'translateX(-50%)',
  zIndex: 3,
  padding: '6px 14px',
  background: '#fdf3e3',
  border: '1px solid #e8c88a',
  borderRadius: 8,
  fontSize: 12,
  color: '#7a5a1e',
  fontFamily: overlayFont,
};

// ---------------------------------------------------------------------------
// 主组件
// ---------------------------------------------------------------------------

function KnowledgeGraph3DInner(props: KnowledgeGraph3DProps): React.ReactElement {
  const {
    data,
    onNodeClick,
    onTracePrerequisites,
    highlightShared = true,
    layout = 'force',
    maxNodes = DEFAULT_MAX_NODES,
    showLegend = true,
    width,
    height,
    className,
    style,
  } = props;

  // ---- 数据: 截断 + contentHash 引用缓存(引用不变 → 力导布局不重置) ----
  const prepared = useMemo(() => truncateGraph(data, maxNodes), [data, maxNodes]);
  const effectiveHash = `${prepared.graph.contentHash}#${prepared.graph.nodes.length}`;
  const graphDataCacheRef = useRef<{ hash: string; graphData: GraphData<RuntimeNode, RuntimeLink> } | null>(null);
  const graphData = useMemo<GraphData<RuntimeNode, RuntimeLink>>(() => {
    const cached = graphDataCacheRef.current;
    if (cached && cached.hash === effectiveHash) {
      return cached.graphData;
    }
    // 力导引擎会原地修改节点/边对象(注入 x/y/z、把 source/target 换成引用), 必须拷贝契约数据
    const next: GraphData<RuntimeNode, RuntimeLink> = {
      nodes: prepared.graph.nodes.map((n) => ({ ...n })),
      links: prepared.graph.links.map((l) => ({ ...l })),
    };
    graphDataCacheRef.current = { hash: effectiveHash, graphData: next };
    return next;
  }, [prepared, effectiveHash]);

  // ---- 布局模式(工具栏可本地切换; prop 变化时同步回受控值) ----
  const [layoutMode, setLayoutMode] = useState<GraphLayoutMode>(layout);
  useEffect(() => setLayoutMode(layout), [layout]);

  // ---- 尺寸: 固定值优先, 否则 ResizeObserver 跟随容器 ----
  const containerRef = useRef<HTMLDivElement | null>(null);
  const fixedSize = width !== undefined || height !== undefined;
  const [size, setSize] = useState({ width: width ?? FALLBACK_WIDTH, height: height ?? FALLBACK_HEIGHT });
  useEffect(() => {
    if (fixedSize) {
      setSize({ width: width ?? FALLBACK_WIDTH, height: height ?? FALLBACK_HEIGHT });
      return;
    }
    const el = containerRef.current;
    if (!el || typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (!entry) return;
      const w = Math.round(entry.contentRect.width);
      const h = Math.round(entry.contentRect.height);
      if (w > 0 && h > 0) {
        setSize((prev) => (prev.width === w && prev.height === h ? prev : { width: w, height: h }));
      }
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, [fixedSize, width, height]);

  // ---- 选中节点(reroot/数据变化后清空) ----
  const [selectedId, setSelectedId] = useState<string | null>(null);
  useEffect(() => setSelectedId(null), [effectiveHash]);
  const selectedNode = useMemo(
    () => graphData.nodes.find((n) => String(n.id) === selectedId) ?? null,
    [graphData, selectedId],
  );

  // ---- 力导实例与布局副作用 ----
  const fgRef = useRef<GraphHandle>();
  const fitPendingRef = useRef(true);
  useEffect(() => {
    fitPendingRef.current = true;
  }, [effectiveHash]);

  /**
   * layered 布局: 用锚点坐标 fz 把节点钉在 layer * LAYER_Z_SPACING 平面(自定义力思路)。
   * 不直接用 dagMode 的原因: layer 是服务端算好的"最长拓扑距离", 直接锚定完全贴合契约语义,
   * 而 dagMode 会在前端按边重新推导层级, 可能与服务端 layer 不一致。
   */
  useEffect(() => {
    for (const node of graphData.nodes) {
      if (layoutMode === 'layered') {
        node.fz = node.layer * LAYER_Z_SPACING;
      } else {
        delete node.fz;
      }
    }
    const fg = fgRef.current;
    if (!fg) return;
    fitPendingRef.current = true;
    fg.d3ReheatSimulation();
    if (layoutMode === 'layered') {
      const maxLayer = graphData.nodes.reduce((m, n) => Math.max(m, n.layer), 0);
      const midZ = (maxLayer * LAYER_Z_SPACING) / 2;
      fg.cameraPosition({ x: 150, y: 36, z: midZ + 60 }, { x: 0, y: 0, z: midZ }, 900);
    }
  }, [layoutMode, graphData]);

  // highlightShared 变化 → 重建全部自定义 three 对象
  useEffect(() => {
    fgRef.current?.refresh();
  }, [highlightShared]);

  const handleEngineStop = useCallback(() => {
    if (!fitPendingRef.current) return;
    fitPendingRef.current = false;
    fgRef.current?.zoomToFit(600, 48);
  }, []);

  // ---- 交互回调 ----
  const handleNodeClick = useCallback(
    (node: GraphNodeObject) => {
      setSelectedId(String(node.id));
      onNodeClick?.(node);
    },
    [onNodeClick],
  );

  const handleNodeRightClick = useCallback(
    (node: GraphNodeObject) => {
      setSelectedId(String(node.id));
      onTracePrerequisites?.(node);
    },
    [onTracePrerequisites],
  );

  const handleBackgroundClick = useCallback(() => setSelectedId(null), []);

  const buildNode = useCallback((node: GraphNodeObject) => buildNodeObject(node, highlightShared), [highlightShared]);
  const buildLink = useCallback((link: GraphLinkObject) => buildLinkObject(link), []);
  const updateLink = useCallback(
    (obj: THREE.Object3D, coords: { start: Vec3; end: Vec3 }) => updateLinkPosition(obj, coords),
    [],
  );

  const masteryDesc = selectedNode ? masteryVisual(selectedNode.mastery) : null;

  return (
    <div
      ref={containerRef}
      className={className}
      style={{
        position: 'relative',
        width: '100%',
        height: '100%',
        minHeight: 320,
        overflow: 'hidden',
        background: GRAPH_THEME.background,
        ...style,
      }}
    >
      <ForceGraph3D<RuntimeNode, RuntimeLink>
        ref={fgRef}
        graphData={graphData}
        width={size.width}
        height={size.height}
        backgroundColor={GRAPH_THEME.background}
        showNavInfo={false}
        nodeThreeObject={buildNode}
        nodeLabel={tooltipOf}
        linkThreeObject={buildLink}
        linkPositionUpdate={updateLink}
        linkDirectionalArrowLength={2.5}
        linkDirectionalArrowRelPos={0.85}
        linkDirectionalArrowColor={(link: GraphLinkObject) => linkStyle({ scope: link.scope }).color}
        onNodeClick={handleNodeClick}
        onNodeRightClick={handleNodeRightClick}
        onBackgroundClick={handleBackgroundClick}
        onEngineStop={handleEngineStop}
        warmupTicks={60}
        cooldownTime={9000}
      />

      {showLegend && (
        <div style={legendDockStyle}>
          <Legend />
        </div>
      )}

      <div style={toolbarStyle}>
        <button
          type="button"
          style={{
            ...toolButtonStyle,
            fontWeight: layoutMode === 'force' ? 600 : 400,
            borderColor: layoutMode === 'force' ? GRAPH_THEME.textSecondary : GRAPH_THEME.panelBorder,
          }}
          onClick={() => setLayoutMode('force')}
          title="自由力导布局"
        >
          力导
        </button>
        <button
          type="button"
          style={{
            ...toolButtonStyle,
            fontWeight: layoutMode === 'layered' ? 600 : 400,
            borderColor: layoutMode === 'layered' ? GRAPH_THEME.textSecondary : GRAPH_THEME.panelBorder,
          }}
          onClick={() => setLayoutMode('layered')}
          title="按 layer(服务端最长拓扑距离)沿 z 轴分层"
        >
          分层
        </button>
      </div>

      {prepared.truncated && (
        <div style={truncBannerStyle}>
          节点过多, 已按 layer 从浅到深截断: 显示 {prepared.graph.nodes.length} / {prepared.totalNodes} 个节点
        </div>
      )}

      <div style={panelStyle}>
        {selectedNode && masteryDesc ? (
          <div>
            <div style={{ fontWeight: 600, marginBottom: 2 }}>{selectedNode.title}</div>
            <div style={{ color: GRAPH_THEME.textSecondary }}>
              {masteryDesc.label} · 得分 {formatScore(selectedNode.score)} · layer {selectedNode.layer}
              {isSharedNode(selectedNode) ? ` · 共享×${selectedNode.sharedByQuestions}` : ''}
            </div>
            <div style={{ marginTop: 8, display: 'flex', gap: 6 }}>
              <button
                type="button"
                style={{
                  ...toolButtonStyle,
                  opacity: onTracePrerequisites ? 1 : 0.5,
                  cursor: onTracePrerequisites ? 'pointer' : 'not-allowed',
                }}
                disabled={!onTracePrerequisites}
                onClick={() => onTracePrerequisites?.(selectedNode)}
                title="占位回调: 追溯该节点的前置知识链"
              >
                追溯前置
              </button>
              <button type="button" style={toolButtonStyle} onClick={() => setSelectedId(null)}>
                取消选中
              </button>
            </div>
          </div>
        ) : (
          <div style={{ color: GRAPH_THEME.textSecondary }}>
            左键节点 = 以其为根(reroot) · 右键节点 = 追溯前置 · 拖拽旋转 / 滚轮缩放
          </div>
        )}
      </div>

      <div style={statusBarStyle}>
        节点 {prepared.graph.nodes.length} · 边 {prepared.graph.links.length} · #{shortHash(prepared.graph.contentHash)}
      </div>
    </div>
  );
}

/**
 * React.memo 自定义比较: data 只比 contentHash(引用变了但内容未变 → 跳过重渲染),
 * 其余 props 做引用/值比较。
 */
function propsAreEqual(prev: KnowledgeGraph3DProps, next: KnowledgeGraph3DProps): boolean {
  return (
    prev.data.contentHash === next.data.contentHash &&
    prev.onNodeClick === next.onNodeClick &&
    prev.onTracePrerequisites === next.onTracePrerequisites &&
    prev.highlightShared === next.highlightShared &&
    prev.layout === next.layout &&
    prev.maxNodes === next.maxNodes &&
    prev.showLegend === next.showLegend &&
    prev.width === next.width &&
    prev.height === next.height &&
    prev.className === next.className &&
    prev.style === next.style
  );
}

export const KnowledgeGraph3D = React.memo(KnowledgeGraph3DInner, propsAreEqual);
KnowledgeGraph3D.displayName = 'KnowledgeGraph3D';
