import type { AiJob, AiJobEvent, ReaderQuestion } from '../types/api'

export function QuestionQueueDetail({
  activeAiJobs,
  jobEvents,
  readerQuestions,
  onCancelAiJob,
  onDeleteReaderQuestion,
  onDismissReaderQuestion,
  onLoadJobEvents,
  onOpenJobTarget,
  onOpenQuestionTarget,
  onRejectAiJob,
  onRetryAiJob,
  onReviewAiJob,
}: {
  activeAiJobs: AiJob[]
  jobEvents: Record<number, AiJobEvent[]>
  readerQuestions: ReaderQuestion[]
  onCancelAiJob: (job: AiJob) => void
  onDeleteReaderQuestion: (question: ReaderQuestion) => void
  onDismissReaderQuestion: (question: ReaderQuestion) => void
  onLoadJobEvents: (job: AiJob) => void
  onOpenJobTarget: (job: AiJob) => void
  onOpenQuestionTarget: (question: ReaderQuestion) => void
  onRejectAiJob: (job: AiJob) => void
  onRetryAiJob: (job: AiJob) => void
  onReviewAiJob: (job: AiJob) => void
}) {
  return (
    <div className="queue-detail">
      <h2>Q Queue</h2>
      <p>
        Track open questions and AI jobs together. Drafts wait here until you explicitly review and
        save them.
      </p>
      <section className="detail-section">
        <h3>Open questions</h3>
        {readerQuestions.length ? (
          <div className="job-list">
            {readerQuestions.map((item) => (
              <article className="ai-revision-card compact-card" key={item.id}>
                <p className="eyebrow">
                  Q #{item.id} / {item.target_type} / {item.target_id} / {item.status}
                </p>
                <h3>{item.question}</h3>
                <div className="question-card-actions">
                  <button type="button" className="focus-toggle" onClick={() => onOpenQuestionTarget(item)}>
                    Open target
                  </button>
                  <button type="button" className="focus-toggle" onClick={() => onDismissReaderQuestion(item)}>
                    Dismiss
                  </button>
                  <button type="button" className="focus-toggle danger-button" onClick={() => onDeleteReaderQuestion(item)}>
                    Delete
                  </button>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <p>No open reader questions.</p>
        )}
      </section>
      <section className="detail-section">
        <h3>AI jobs</h3>
        {activeAiJobs.length ? (
          <div className="job-list">
            {activeAiJobs.map((job) => (
              <article className="ai-revision-card" key={job.id}>
                <p className="eyebrow">
                  Job #{job.id} / {job.target_type} / {job.target_id}
                </p>
                <h3>{job.status}: {job.stage}</h3>
                {job.revision?.summary && <p>{job.revision.summary}</p>}
                {(job.error_summary || job.error) && (
                  <p className="ai-status error">{job.error_summary || job.error}</p>
                )}
                <div className="question-card-actions">
                  {job.status === 'draft_ready' && job.revision && (
                    <button type="button" className="focus-toggle ai-action" onClick={() => onReviewAiJob(job)}>
                      Review draft
                    </button>
                  )}
                  <button type="button" className="focus-toggle" onClick={() => onOpenJobTarget(job)}>
                    Open target
                  </button>
                  {job.status === 'failed' && (
                    <button type="button" className="focus-toggle ai-action" onClick={() => onRetryAiJob(job)}>
                      Retry
                    </button>
                  )}
                  {job.status === 'draft_ready' && (
                    <button type="button" className="focus-toggle" onClick={() => onRejectAiJob(job)}>
                      Reject draft
                    </button>
                  )}
                  <button type="button" className="focus-toggle" onClick={() => onLoadJobEvents(job)}>
                    Show events
                  </button>
                  {['queued', 'solving'].includes(job.status) && (
                    <button type="button" className="focus-toggle" onClick={() => onCancelAiJob(job)}>
                      Cancel
                    </button>
                  )}
                </div>
                {jobEvents[job.id]?.length > 0 && (
                  <ol className="job-event-list">
                    {jobEvents[job.id].map((event) => (
                      <li key={event.id}>
                        <strong>{event.stage}</strong>: {event.message}
                      </li>
                    ))}
                  </ol>
                )}
              </article>
            ))}
          </div>
        ) : (
          <p>No AI jobs yet.</p>
        )}
      </section>
    </div>
  )
}
