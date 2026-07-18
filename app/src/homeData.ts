import { fetchJson } from './lib/apiClient'
import type {
  ApiAreasResponse,
  ApiBiteResponse,
  ApiDueReviewsResponse,
  ApiKgBottlenecksResponse,
  ApiKgQuestionsResponse,
  ApiNodesResponse,
  ApiReaderQuestionsResponse,
  ApiSyncHealthResponse,
  DailyBite,
  NodeSummary,
  SyncHealth,
} from './types/api'

export type HomeSummary = {
  recentNode: NodeSummary | null
  totalNodes: number | null
  dueReviewCount: number | null
  openQuestionCount: number | null
  dailyBite: DailyBite | null
  kgRootCount: number | null
  bottleneckCount: number | null
  syncHealth: SyncHealth | null
  warnings: string[]
}

export const emptyHomeSummary: HomeSummary = {
  recentNode: null,
  totalNodes: null,
  dueReviewCount: null,
  openQuestionCount: null,
  dailyBite: null,
  kgRootCount: null,
  bottleneckCount: null,
  syncHealth: null,
  warnings: [],
}

function warningFor(label: string, error: unknown) {
  const detail = error instanceof Error ? error.message : 'unavailable'
  return `${label}: ${detail}`
}

export async function loadHomeSummary(): Promise<HomeSummary> {
  const [recentNodes, areas, reviews, questions, bite, kgQuestions, bottlenecks, syncHealth] =
    await Promise.allSettled([
      fetchJson<ApiNodesResponse>('/api/search?sort=last-read'),
      fetchJson<ApiAreasResponse>('/api/areas'),
      fetchJson<ApiDueReviewsResponse>('/api/review/due?limit=50'),
      fetchJson<ApiReaderQuestionsResponse>('/api/reader-questions?status=active'),
      fetchJson<ApiBiteResponse>('/api/bite/daily'),
      fetchJson<ApiKgQuestionsResponse>('/api/kg/questions'),
      fetchJson<ApiKgBottlenecksResponse>('/api/kg/bottlenecks?minDistinctQuestions=1&limit=10'),
      fetchJson<ApiSyncHealthResponse>('/api/sync/v1/health'),
    ])

  const summary: HomeSummary = { ...emptyHomeSummary, warnings: [] }

  if (recentNodes.status === 'fulfilled') summary.recentNode = recentNodes.value.nodes[0] ?? null
  else summary.warnings.push(warningFor('Recent node', recentNodes.reason))
  if (areas.status === 'fulfilled') summary.totalNodes = areas.value.system.all ?? null
  else summary.warnings.push(warningFor('Node count', areas.reason))
  if (reviews.status === 'fulfilled') summary.dueReviewCount = reviews.value.reviews.length
  else summary.warnings.push(warningFor('Review queue', reviews.reason))
  if (questions.status === 'fulfilled') summary.openQuestionCount = questions.value.questions.length
  else summary.warnings.push(warningFor('Assistant queue', questions.reason))
  if (bite.status === 'fulfilled') summary.dailyBite = bite.value.bite
  else summary.warnings.push(warningFor('Daily Bite', bite.reason))
  if (kgQuestions.status === 'fulfilled') summary.kgRootCount = kgQuestions.value.total
  else summary.warnings.push(warningFor('Knowledge graph roots', kgQuestions.reason))
  if (bottlenecks.status === 'fulfilled') summary.bottleneckCount = bottlenecks.value.items.length
  else summary.warnings.push(warningFor('Knowledge graph bottlenecks', bottlenecks.reason))
  if (syncHealth.status === 'fulfilled') summary.syncHealth = syncHealth.value
  else summary.warnings.push(warningFor('Sync health', syncHealth.reason))

  return summary
}
