import type { NodeSortKey, QuizSortKey, SortOption } from './searchSort'

type SearchMode = 'nodes' | 'quizzes' | 'question-queue'

type SearchHeaderControlsProps = {
  mode: SearchMode
  query: string
  nodeSort: NodeSortKey
  quizSort: QuizSortKey
  nodeSortOptions: Array<SortOption<NodeSortKey>>
  quizSortOptions: Array<SortOption<QuizSortKey>>
  isLoading: boolean
  visibleCount: number
  totalCount: number
  isNewNodeOpen: boolean
  onQueryChange: (query: string) => void
  onNodeSortChange: (sort: NodeSortKey) => void
  onQuizSortChange: (sort: QuizSortKey) => void
  onNewNodeToggle: () => void
  isWorkbench?: boolean
  onClearQuery?: () => void
  onExpandAll?: () => void
  onCollapseAll?: () => void
}

function searchLabel(mode: SearchMode) {
  if (mode === 'question-queue') return 'Question queue'
  if (mode === 'quizzes') return 'Quiz search'
  return 'Global search'
}

function searchPlaceholder(mode: SearchMode) {
  if (mode === 'question-queue') return 'Open questions are loaded directly...'
  if (mode === 'quizzes') return 'Search quiz prompts, tags, answers...'
  return 'Search concepts, tags, summaries...'
}

function indexedNoun(mode: SearchMode) {
  if (mode === 'question-queue') return 'open questions'
  if (mode === 'quizzes') return 'quizzes'
  return 'nodes'
}

export function SearchHeaderControls({
  mode,
  query,
  nodeSort,
  quizSort,
  nodeSortOptions,
  quizSortOptions,
  isLoading,
  visibleCount,
  totalCount,
  isNewNodeOpen,
  onQueryChange,
  onNodeSortChange,
  onQuizSortChange,
  onNewNodeToggle,
  isWorkbench = false,
  onClearQuery,
  onExpandAll,
  onCollapseAll,
}: SearchHeaderControlsProps) {
  return (
    <header className={`search-header${isWorkbench ? ' library-workbench-toolbar' : ''}`}>
      <label htmlFor="node-search">{searchLabel(mode)}</label>
      <div className="search-row">
        <div className="search-input-shell">
          <input
            id="node-search"
            value={query}
            onChange={(event) => onQueryChange(event.target.value)}
            placeholder={searchPlaceholder(mode)}
          />
          {query && onClearQuery && (
            <button type="button" className="search-clear" onClick={onClearQuery} aria-label="Clear search">
              Clear
            </button>
          )}
        </div>
        {mode === 'nodes' && !isWorkbench && (
          <label className="sort-control" htmlFor="node-sort">
            <span>Sort</span>
            <select
              id="node-sort"
              value={nodeSort}
              onChange={(event) => onNodeSortChange(event.target.value as NodeSortKey)}
            >
              {nodeSortOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
        )}
        {mode === 'quizzes' && (
          <label className="sort-control" htmlFor="quiz-sort">
            <span>Sort</span>
            <select
              id="quiz-sort"
              value={quizSort}
              onChange={(event) => onQuizSortChange(event.target.value as QuizSortKey)}
            >
              {quizSortOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>
      <div className="search-header-footer">
        <p>
          {isLoading
            ? 'Loading index...'
            : `${visibleCount} visible of ${totalCount} indexed ${indexedNoun(mode)}`}
        </p>
        {mode === 'nodes' && (
          <div className="search-header-actions">
            {isWorkbench && onExpandAll && onCollapseAll && (
              <>
                <button type="button" className="toolbar-action" onClick={onExpandAll}>Expand all</button>
                <button type="button" className="toolbar-action" onClick={onCollapseAll}>Collapse all</button>
              </>
            )}
            <button type="button" className="focus-toggle new-node-toggle" onClick={onNewNodeToggle}>
              {isNewNodeOpen ? 'Close new node' : '+ New node'}
            </button>
          </div>
        )}
      </div>
    </header>
  )
}
