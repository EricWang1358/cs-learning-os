import {
  defaultNodeSort,
  defaultQuizSort,
  parseNodeSort,
  parseQuizSort,
  type NodeSortKey,
  type QuizSortKey,
} from '../searchSort'
import type { ViewMode } from '../types/api'

export function routeFromLocation(pathname: string, search: string) {
  const params = new URLSearchParams(search)
  const nodeMatch = pathname.match(/^\/nodes\/([^/]+)$/)
  const quizMatch = pathname.match(/^\/quizzes\/([^/]+)$/)
  const isQuizList = pathname === '/quizzes'
  const isQueue = pathname === '/queue'
  const isReview = pathname === '/review'
  const isBite = pathname === '/bite'
  const isGraph = pathname === '/graph' || pathname.startsWith('/graph/')
  const isKnowledgeGraph = pathname === '/knowledge-graph'
  const isHealth = pathname === '/health'
  const isSync = pathname === '/sync'

  const query = params.get('q') || ''
  return {
    viewMode: isKnowledgeGraph
      ? 'kgraph' as ViewMode
      : isGraph
      ? 'graph' as ViewMode
      : isSync
        ? 'sync' as ViewMode
        : isHealth
          ? 'health' as ViewMode
          : isBite
            ? 'bite' as ViewMode
            : isReview
              ? 'review' as ViewMode
              : isQueue
                ? 'question-queue' as ViewMode
                : quizMatch || isQuizList
                  ? 'quizzes' as ViewMode
                  : 'nodes' as ViewMode,
    selectedSlug: nodeMatch ? decodeURIComponent(nodeMatch[1]) : '',
    selectedQuizId: quizMatch ? decodeURIComponent(quizMatch[1]) : '',
    activeArea: params.get('area') || 'all',
    activeTrack: params.get('track') || 'all',
    query,
    nodeSort: parseNodeSort(params.get('sort'), query),
    quizSort: parseQuizSort(params.get('sort'), query),
    graphPage: Number(params.get('page') || '1'),
    isFocusMode: params.get('focus') === '1',
    isWidgetMode: params.get('widget') === '1',
  }
}

export function routeSearch(options: {
  activeArea?: string
  activeTrack?: string
  query?: string
  isFocusMode?: boolean
  page?: number
  nodeSort?: NodeSortKey
  quizSort?: QuizSortKey
}) {
  const params = new URLSearchParams()
  if (options.activeArea && options.activeArea !== 'all') params.set('area', options.activeArea)
  if (options.activeTrack && options.activeTrack !== 'all') params.set('track', options.activeTrack)
  if (options.query) params.set('q', options.query)
  if (options.nodeSort && options.nodeSort !== defaultNodeSort(options.query ?? '')) params.set('sort', options.nodeSort)
  if (options.quizSort && options.quizSort !== defaultQuizSort(options.query ?? '')) params.set('sort', options.quizSort)
  if (options.isFocusMode) params.set('focus', '1')
  if (options.page && options.page > 1) params.set('page', String(options.page))
  const value = params.toString()
  return value ? `?${value}` : ''
}

export function graphApiPath(pathname: string, page: number) {
  const search = routeSearch({ page })
  if (pathname === '/graph') return `/api/graph${search}`
  return `/api${pathname}${search}`
}
