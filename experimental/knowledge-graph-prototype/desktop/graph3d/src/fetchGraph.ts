/**
 * 数据源层: 对接 FastAPI `GET /graph/export3d` (RFC §3.6), 返回 RFC §3.4 的 GraphExport。
 *
 * 要点:
 * - contentHash 增量刷新(客户端侧 304 语义): 传入上次 contentHash, 未变则复用缓存对象,
 *   引用不变 → 下游 useMemo/React.memo 不抖动; 同时发送 If-None-Match 头, 服务端支持 ETag 时直接 304。
 * - 离线开发: apiBase 传本地静态 JSON 的 URL(以 .json 结尾), 或用 exportFromStatic 直接解析内存对象。
 * - 契约校验: 解析即校验, 不合规抛 GraphContractError(快速暴露后端契约漂移)。
 */

import {
  EDGE_SCOPES,
  GRAPH_EXPORT_SCHEMA_VERSION,
  MASTERY_STATES,
} from './types';
import type {
  EdgeScope,
  GraphExport,
  MasteryState,
  SceneLink,
  SceneNode,
} from './types';

/** 契约校验失败(响应不符合 RFC §3.4) */
export class GraphContractError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'GraphContractError';
  }
}

/** HTTP 层失败(非 2xx 或 304 无本地副本) */
export class GraphFetchError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'GraphFetchError';
    this.status = status;
  }
}

/** 拉取结果状态: fresh=拿到新数据; not-modified=contentHash 未变, 返回缓存对象 */
export type FetchStatus = 'fresh' | 'not-modified';

export interface FetchExportResult {
  data: GraphExport;
  status: FetchStatus;
  contentHash: string;
}

export interface FetchExportOptions {
  signal?: AbortSignal;
  /** 注入自定义 fetch (测试/Electron net 模块等) */
  fetchImpl?: typeof fetch;
  /** 额外请求头(如鉴权) */
  extraHeaders?: Record<string, string>;
}

/** 模块级缓存: key 为最终请求 URL, value 为最近一次校验通过的 GraphExport */
const cache = new Map<string, GraphExport>();

/** 清空缓存; 传 key 只清对应条目 */
export function clearExportCache(key?: string): void {
  if (key === undefined) {
    cache.clear();
  } else {
    cache.delete(key);
  }
}

/**
 * 计算请求 URL。
 * - 常规: `${apiBase}/graph/export3d?root=...&rootIsQuestion=...` (RFC §3.6)
 * - 离线: apiBase 以 .json 结尾时视为静态文件地址, 原样返回
 */
export function export3dUrl(apiBase: string, root: string, rootIsQuestion: boolean): string {
  if (/\.json([?#].*)?$/i.test(apiBase)) {
    return apiBase;
  }
  const base = apiBase.replace(/\/+$/, '');
  return `${base}/graph/export3d?root=${encodeURIComponent(root)}&rootIsQuestion=${rootIsQuestion ? 'true' : 'false'}`;
}

/**
 * 拉取 3D 导出数据。
 *
 * @param apiBase        FastAPI 服务前缀(如 http://127.0.0.1:8000), 或本地静态 JSON URL
 * @param root           根 id (问题 id 或节点 id)
 * @param rootIsQuestion root 是否为登记的问题根 (对应 export3d 的 rootIsQuestion 参数)
 * @param lastHash       上一次成功拿到的 contentHash; 未变时返回缓存(304 语义)
 * @param options        signal / fetchImpl / extraHeaders
 */
export async function fetchExport3d(
  apiBase: string,
  root: string,
  rootIsQuestion: boolean,
  lastHash?: string,
  options?: FetchExportOptions,
): Promise<FetchExportResult> {
  const url = export3dUrl(apiBase, root, rootIsQuestion);
  const doFetch = options?.fetchImpl ?? fetch;
  const headers: Record<string, string> = { Accept: 'application/json', ...options?.extraHeaders };
  if (lastHash) {
    // 服务端若实现 ETag, 可直接返回 304 省带宽; 不实现也不影响(走下方客户端对比)
    headers['If-None-Match'] = lastHash;
  }

  const res = await doFetch(url, { headers, signal: options?.signal });

  if (res.status === 304) {
    const hit = cache.get(url);
    if (!hit) {
      throw new GraphFetchError(304, `服务端返回 304 但本地无缓存副本: ${url}`);
    }
    return { data: hit, status: 'not-modified', contentHash: hit.contentHash };
  }
  if (!res.ok) {
    throw new GraphFetchError(res.status, `GET ${url} 失败: HTTP ${res.status}`);
  }

  const raw: unknown = await res.json();
  const data = exportFromStatic(raw);

  if (lastHash && data.contentHash === lastHash) {
    // 304 语义(客户端侧对比): 内容未变, 优先复用缓存对象以保持引用稳定
    const hit = cache.get(url) ?? data;
    cache.set(url, hit);
    return { data: hit, status: 'not-modified', contentHash: hit.contentHash };
  }

  cache.set(url, data);
  return { data, status: 'fresh', contentHash: data.contentHash };
}

/**
 * 解析并校验一份"静态"导出数据(离线开发/单测用)。
 * 兼容两种形态: RFC §3.4 载荷本体, 或 RFC §3.2 的 REST 信封 { payloadJson, ... }。
 * 不合规时抛 GraphContractError。
 */
export function exportFromStatic(raw: unknown): GraphExport {
  let payload: unknown = raw;

  if (isRecord(raw) && 'payloadJson' in raw) {
    const inner: unknown = raw.payloadJson;
    if (typeof inner === 'string') {
      try {
        payload = JSON.parse(inner);
      } catch {
        throw new GraphContractError('信封 payloadJson 不是合法 JSON 字符串');
      }
    } else {
      payload = inner;
    }
  }

  if (!isRecord(payload)) {
    throw new GraphContractError('导出载荷必须是 JSON 对象');
  }
  if (payload.schemaVersion !== GRAPH_EXPORT_SCHEMA_VERSION) {
    throw new GraphContractError(
      `schemaVersion 必须为 ${String(GRAPH_EXPORT_SCHEMA_VERSION)}, 实际: ${describe(payload.schemaVersion)}`,
    );
  }

  const contentHash = requireString(payload, 'contentHash');
  const generatedAt = requireNumber(payload, 'generatedAt');
  const nodes = requireArray(payload, 'nodes').map((item, i) => parseNode(item, i));
  const links = requireArray(payload, 'links').map((item, i) => parseLink(item, i));

  return {
    schemaVersion: GRAPH_EXPORT_SCHEMA_VERSION,
    contentHash,
    generatedAt,
    nodes,
    links,
  };
}

// ---------------------------------------------------------------------------
// 以下为契约校验的内部实现
// ---------------------------------------------------------------------------

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function describe(value: unknown): string {
  if (value === null) return 'null';
  if (Array.isArray(value)) return 'array';
  return typeof value;
}

function requireString(obj: Record<string, unknown>, field: string): string {
  const value = obj[field];
  if (typeof value !== 'string' || value.length === 0) {
    throw new GraphContractError(`字段 ${field} 必须为非空字符串, 实际: ${describe(value)}`);
  }
  return value;
}

function requireNumber(obj: Record<string, unknown>, field: string): number {
  const value = obj[field];
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new GraphContractError(`字段 ${field} 必须为有限数值, 实际: ${describe(value)}`);
  }
  return value;
}

function requireInt(obj: Record<string, unknown>, field: string, where: string): number {
  const value = requireNumber(obj, field);
  if (!Number.isInteger(value) || value < 0) {
    throw new GraphContractError(`${where} 的 ${field} 必须为非负整数, 实际: ${String(value)}`);
  }
  return value;
}

function requireArray(obj: Record<string, unknown>, field: string): unknown[] {
  const value = obj[field];
  if (!Array.isArray(value)) {
    throw new GraphContractError(`字段 ${field} 必须为数组, 实际: ${describe(value)}`);
  }
  return value;
}

function parseNode(raw: unknown, index: number): SceneNode {
  const where = `nodes[${index}]`;
  if (!isRecord(raw)) {
    throw new GraphContractError(`${where} 必须是对象, 实际: ${describe(raw)}`);
  }
  const id = requireString(raw, 'id');
  const title = requireString(raw, 'title');
  const layer = requireInt(raw, 'layer', where);
  const masteryRaw = requireString(raw, 'mastery');
  if (!MASTERY_STATES.includes(masteryRaw as MasteryState)) {
    throw new GraphContractError(`${where}.mastery 非法: ${masteryRaw} (应为 ${MASTERY_STATES.join('/')})`);
  }
  const score = requireNumber(raw, 'score');
  if (score < 0 || score > 1) {
    throw new GraphContractError(`${where}.score 必须在 [0,1], 实际: ${String(score)}`);
  }
  const parentCount = requireInt(raw, 'parentCount', where);
  const sharedByQuestions = requireInt(raw, 'sharedByQuestions', where);
  const isRootRaw = raw.isRoot;
  if (typeof isRootRaw !== 'boolean') {
    throw new GraphContractError(`${where}.isRoot 必须为布尔值, 实际: ${describe(isRootRaw)}`);
  }
  return {
    id,
    title,
    layer,
    mastery: masteryRaw as MasteryState,
    score,
    parentCount,
    sharedByQuestions,
    isRoot: isRootRaw,
  };
}

function parseLink(raw: unknown, index: number): SceneLink {
  const where = `links[${index}]`;
  if (!isRecord(raw)) {
    throw new GraphContractError(`${where} 必须是对象, 实际: ${describe(raw)}`);
  }
  const source = requireString(raw, 'source');
  const target = requireString(raw, 'target');
  const scopeRaw = requireString(raw, 'scope');
  if (!EDGE_SCOPES.includes(scopeRaw as EdgeScope)) {
    throw new GraphContractError(`${where}.scope 非法: ${scopeRaw} (应为 ${EDGE_SCOPES.join('/')})`);
  }
  return { source, target, scope: scopeRaw as EdgeScope };
}
