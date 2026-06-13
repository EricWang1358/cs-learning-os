import { useState, type ReactNode } from 'react'

export type DailyReviewRating = 'again' | 'hard' | 'good' | 'easy'

export type DailyReviewItem = {
  id: string
  quizId: string
  title: string
  summary?: string
  area?: string
  difficulty?: string
  dueAt?: string
  intervalLabel?: string
  tags?: string[]
}

export type DailyReviewQuiz = {
  id: string
  title: string
  summary?: string
  area?: string
  difficulty?: string
  body?: string
  answer?: ReactNode
  tags?: string[]
  openQuestionCount?: number
}

type DailyReviewBodySlotProps = {
  quiz: DailyReviewQuiz
  isAnswerRevealed: boolean
  onRevealAnswer: () => void
}

export function DailyReviewPanel({
  reviews,
  selectedQuiz,
  isReviewsLoading,
  isQuizLoading,
  isRating,
  reviewsError,
  quizError,
  renderQuizBody,
  onSelectReview,
  onRevealAnswer,
  onRateReview,
}: {
  reviews: DailyReviewItem[]
  selectedQuiz: DailyReviewQuiz | null
  isReviewsLoading?: boolean
  isQuizLoading?: boolean
  isRating?: boolean
  reviewsError?: string
  quizError?: string
  renderQuizBody?: (props: DailyReviewBodySlotProps) => ReactNode
  onSelectReview: (review: DailyReviewItem) => void
  onRevealAnswer?: (quiz: DailyReviewQuiz) => void
  onRateReview: (rating: DailyReviewRating, review: DailyReviewItem | null, quiz: DailyReviewQuiz) => void
}) {
  const [revealedQuizId, setRevealedQuizId] = useState<string | null>(null)
  const selectedReview = reviews.find((review) => review.quizId === selectedQuiz?.id) ?? null
  const isAnswerRevealed = Boolean(selectedQuiz && revealedQuizId === selectedQuiz.id)

  const revealAnswer = () => {
    if (!selectedQuiz) return
    setRevealedQuizId(selectedQuiz.id)
    onRevealAnswer?.(selectedQuiz)
  }

  const rateReview = (rating: DailyReviewRating) => {
    if (!selectedQuiz || !isAnswerRevealed) return
    onRateReview(rating, selectedReview, selectedQuiz)
  }

  const renderReviewMeta = (review: DailyReviewItem) => {
    const parts = [review.area, review.difficulty, review.intervalLabel].filter(Boolean)
    return parts.length ? parts.join(' / ') : 'Review'
  }

  return (
    <div className="queue-detail" aria-label="Daily review workspace">
      <section className="detail-section" aria-label="Daily review queue">
        <h3>Due reviews</h3>
        {reviewsError && <p className="error-banner">{reviewsError}</p>}
        {isReviewsLoading && !reviews.length ? (
          <p className="detail-loading">Loading due reviews...</p>
        ) : reviews.length ? (
          <div className="job-list">
            {reviews.map((review) => (
              <button
                key={review.id}
                type="button"
                className={`node-card ${review.quizId === selectedQuiz?.id ? 'selected' : ''}`}
                onClick={() => onSelectReview(review)}
              >
                <span className="node-meta">{renderReviewMeta(review)}</span>
                <strong>{review.title}</strong>
                {review.summary && <span>{review.summary}</span>}
                {review.dueAt && <span>Due {review.dueAt}</span>}
              </button>
            ))}
          </div>
        ) : (
          <div className="empty-state">
            <h2>No reviews due</h2>
            <p>Your daily review queue is clear.</p>
          </div>
        )}
      </section>

      <section className="detail-section" aria-label="Selected quiz review">
        {quizError && <p className="error-banner">{quizError}</p>}
        {isQuizLoading && !selectedQuiz ? (
          <p className="detail-loading">Loading quiz...</p>
        ) : selectedQuiz ? (
          <div className="detail-main">
            <div className="detail-heading">
              <p className="eyebrow">{selectedQuiz.area ? `${selectedQuiz.area} / quiz` : 'quiz'}</p>
              <h2>{selectedQuiz.title}</h2>
              {selectedQuiz.summary && <p>{selectedQuiz.summary}</p>}
            </div>

            <div className="tag-row">
              {selectedQuiz.openQuestionCount ? (
                <span className="needs-review">Q to be solved: {selectedQuiz.openQuestionCount}</span>
              ) : null}
              {selectedQuiz.difficulty && <span>{selectedQuiz.difficulty}</span>}
              {selectedQuiz.tags?.map((tag) => <span key={tag}>{tag}</span>)}
            </div>

            <section className="detail-section">
              <h3>Quiz body</h3>
              {renderQuizBody ? (
                renderQuizBody({ quiz: selectedQuiz, isAnswerRevealed, onRevealAnswer: revealAnswer })
              ) : selectedQuiz.body ? (
                <p>{selectedQuiz.body}</p>
              ) : (
                <p>No quiz body provided.</p>
              )}
            </section>

            <section className="detail-section">
              <h3>Answer</h3>
              {isAnswerRevealed ? (
                selectedQuiz.answer ? <div>{selectedQuiz.answer}</div> : <p>No answer provided.</p>
              ) : (
                <button type="button" className="focus-toggle ai-action" onClick={revealAnswer}>
                  Reveal answer
                </button>
              )}
            </section>

            <div className="question-card-actions" aria-label="Review rating">
              <button
                type="button"
                className="focus-toggle"
                disabled={!isAnswerRevealed || isRating}
                onClick={() => rateReview('again')}
              >
                Again
              </button>
              <button
                type="button"
                className="focus-toggle"
                disabled={!isAnswerRevealed || isRating}
                onClick={() => rateReview('hard')}
              >
                Hard
              </button>
              <button
                type="button"
                className="focus-toggle"
                disabled={!isAnswerRevealed || isRating}
                onClick={() => rateReview('good')}
              >
                Good
              </button>
              <button
                type="button"
                className="focus-toggle"
                disabled={!isAnswerRevealed || isRating}
                onClick={() => rateReview('easy')}
              >
                Easy
              </button>
            </div>

            {isQuizLoading && <p className="detail-loading">Refreshing quiz...</p>}
          </div>
        ) : (
          <div className="empty-state">
            <h2>Select a review</h2>
            <p>Choose a due quiz to start your daily review.</p>
          </div>
        )}
      </section>
    </div>
  )
}
