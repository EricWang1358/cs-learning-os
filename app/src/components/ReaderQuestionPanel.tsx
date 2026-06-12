import type { ReaderQuestion } from '../types/api'

export function ReaderQuestionPanel({
  aiDraftHint,
  aiStatus,
  aiStatusClass,
  aiStatusText,
  isAiRevising,
  isQuestionSaving,
  openQuestionCount,
  placeholder,
  questionDraft,
  questionFeedback,
  readerQuestionHint,
  visibleReaderQuestions,
  onDraftWithAi,
  onQuestionDraftChange,
  onSubmitQuestion,
}: {
  aiDraftHint: string
  aiStatus: string
  aiStatusClass: string
  aiStatusText: string
  isAiRevising: boolean
  isQuestionSaving: boolean
  openQuestionCount: number
  placeholder: string
  questionDraft: string
  questionFeedback: string
  readerQuestionHint: string
  visibleReaderQuestions: ReaderQuestion[]
  onDraftWithAi: () => void
  onQuestionDraftChange: (value: string) => void
  onSubmitQuestion: () => void
}) {
  return (
    <aside className="reader-question-panel" aria-label="Reader questions">
      <div>
        <p className="eyebrow">Q to be solved</p>
        <h3>What is unclear?</h3>
        <p>
          Save questions while reading. Later, ask the LLM to fold them back into the
          tutorial.
        </p>
      </div>
      <textarea
        value={questionDraft}
        onChange={(event) => onQuestionDraftChange(event.target.value)}
        placeholder={placeholder}
      />
      {(readerQuestionHint || aiDraftHint) && (
        <p className={questionFeedback ? 'inline-success' : 'inline-hint'}>
          {questionFeedback || aiDraftHint || readerQuestionHint}
        </p>
      )}
      <button
        type="button"
        className="focus-toggle"
        disabled={isQuestionSaving}
        onClick={onSubmitQuestion}
      >
        {isQuestionSaving ? 'Saving...' : 'Save question'}
      </button>
      <button
        type="button"
        className="focus-toggle ai-action"
        disabled={isAiRevising}
        onClick={onDraftWithAi}
      >
        {isAiRevising ? 'Drafting...' : 'Draft with AI'}
      </button>
      {aiStatus && <p className={aiStatusClass}>{aiStatusText}</p>}
      <div className="reader-question-list">
        <strong>{openQuestionCount} open</strong>
        {visibleReaderQuestions.map((item) => (
          <p key={item.id}>{item.question}</p>
        ))}
      </div>
    </aside>
  )
}
