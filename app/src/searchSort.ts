export type SortOption<T extends string> = {
  value: T
  label: string
}

export type NodeSortKey = 'relevance' | 'last-edit' | 'last-read' | 'order' | 'alphabet'
export type QuizSortKey = 'relevance' | 'last-edit' | 'difficulty' | 'order' | 'alphabet'

export const nodeSortOptions: Array<SortOption<NodeSortKey>> = [
  { value: 'relevance', label: 'Relevance' },
  { value: 'last-edit', label: 'Last edit' },
  { value: 'last-read', label: 'Last read' },
  { value: 'order', label: 'Learning order' },
  { value: 'alphabet', label: 'A-Z' },
]

export const quizSortOptions: Array<SortOption<QuizSortKey>> = [
  { value: 'relevance', label: 'Relevance' },
  { value: 'last-edit', label: 'Last edit' },
  { value: 'difficulty', label: 'Difficulty' },
  { value: 'order', label: 'Quiz order' },
  { value: 'alphabet', label: 'A-Z' },
]

export function defaultNodeSort(query: string): NodeSortKey {
  return query.trim() ? 'relevance' : 'order'
}

export function defaultQuizSort(query: string): QuizSortKey {
  return query.trim() ? 'relevance' : 'order'
}

export function parseNodeSort(value: string | null, query: string): NodeSortKey {
  if (value === 'relevance' && !query.trim()) return 'order'
  if (value && nodeSortOptions.some((option) => option.value === value)) return value as NodeSortKey
  return defaultNodeSort(query)
}

export function parseQuizSort(value: string | null, query: string): QuizSortKey {
  if (value === 'relevance' && !query.trim()) return 'order'
  if (value && quizSortOptions.some((option) => option.value === value)) return value as QuizSortKey
  return defaultQuizSort(query)
}

export function visibleNodeSortOptions(query: string) {
  return query.trim() ? nodeSortOptions : nodeSortOptions.filter((option) => option.value !== 'relevance')
}

export function visibleQuizSortOptions(query: string) {
  return query.trim() ? quizSortOptions : quizSortOptions.filter((option) => option.value !== 'relevance')
}
