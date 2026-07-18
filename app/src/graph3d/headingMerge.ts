/**
 * reroot 单节点时, 把该节点 markdown 的章节子标题合并为"卫星节点"放进 3D 场景——
 * 参照导航图谱(GET /api/graph/node/{slug})的 node 层(center + heading children)。
 *
 * 纯前端合并, 不改 kg 服务端契约: heading 节点/边带 kind='heading' 标记,
 * 视觉(小八面体 + 细虚线)与交互(点击打开笔记章节而非 reroot)都据此分流。
 */

import type { GraphExport, SceneLink, SceneNode } from './types';

/** 导航图谱 node 层返回的 heading 子项 (backend graph_service.markdown_headings) */
export interface NavigationHeadingItem {
  type: string;
  id: string;
  label: string;
  meta: string;
  href: string;
  level?: number;
}

/** djb2 短哈希: heading 集合签名并入 contentHash, 使 memo/useMemo 键随子标题变化 */
function tinyHash(text: string): string {
  let hash = 5381;
  for (let i = 0; i < text.length; i += 1) {
    hash = ((hash << 5) + hash + text.charCodeAt(i)) | 0;
  }
  return (hash >>> 0).toString(36);
}

export function headingNodeId(rootId: string, headingId: string): string {
  return `${rootId}::${headingId}`;
}

/**
 * 把 headings 合并进导出数据; 幂等(已存在的卫星按 id 跳过)。
 * 返回新对象(引用变化), contentHash 追加 heading 签名。
 */
export function mergeHeadingsIntoExport(
  data: GraphExport,
  rootId: string,
  headings: NavigationHeadingItem[],
): GraphExport {
  // 跳过 H1(level<=1): 正文一级标题通常就是笔记标题本身, 与 ROOT 标注重复。
  const items = headings.filter(
    (h) => h && h.type === 'heading' && h.id && h.label && (h.level ?? 2) > 1,
  );
  if (items.length === 0) return data;

  const existing = new Set(data.nodes.map((n) => n.id));
  const nodes: SceneNode[] = [];
  const links: SceneLink[] = [];
  for (const item of items) {
    const id = headingNodeId(rootId, item.id);
    if (existing.has(id)) continue;
    nodes.push({
      id,
      title: item.label,
      layer: 1,
      mastery: 'UNKNOWN',
      score: 0,
      parentCount: 1,
      sharedByQuestions: 0,
      isRoot: false,
      kind: 'heading',
      href: item.href,
      headingLevel: item.level,
    });
    links.push({ source: rootId, target: id, scope: 'GLOBAL', kind: 'heading' });
  }
  if (nodes.length === 0) return data;

  const signature = tinyHash(items.map((h) => h.id).join('|'));
  return {
    ...data,
    contentHash: `${data.contentHash}+h${signature}`,
    nodes: [...data.nodes, ...nodes],
    links: [...data.links, ...links],
  };
}
