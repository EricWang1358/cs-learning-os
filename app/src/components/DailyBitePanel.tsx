import { useCallback, useEffect, useMemo, useState } from 'react'
import { deleteJson, fetchJson, postJson, putJson } from '../lib/apiClient'
import type { ApiBiteCardResponse, ApiBiteResponse, ApiBitesResponse, BiteCardPayload, DailyBite } from '../types/api'

type BiteProgress = {
  version: 1
  streak: number
  lastCompletedDate: string
  completedCount: number
}

const PROGRESS_KEY = 'learning-os-daily-bite-progress:v1'

const emptyProgress: BiteProgress = {
  version: 1,
  streak: 0,
  lastCompletedDate: '',
  completedCount: 0,
}

function todayKey() {
  return new Date().toISOString().slice(0, 10)
}

function yesterdayKey() {
  const yesterday = new Date()
  yesterday.setDate(yesterday.getDate() - 1)
  return yesterday.toISOString().slice(0, 10)
}

function readProgress(): BiteProgress {
  try {
    const raw = window.localStorage.getItem(PROGRESS_KEY)
    if (!raw) return emptyProgress
    const parsed = JSON.parse(raw) as Partial<BiteProgress>
    if (parsed.version !== 1) return emptyProgress
    return {
      version: 1,
      streak: Number(parsed.streak) || 0,
      lastCompletedDate: parsed.lastCompletedDate || '',
      completedCount: Number(parsed.completedCount) || 0,
    }
  } catch {
    return emptyProgress
  }
}

function writeProgress(progress: BiteProgress) {
  window.localStorage.setItem(PROGRESS_KEY, JSON.stringify(progress))
}

function normalizeAnswer(value: string) {
  return value
    .toLowerCase()
    .replace(/[`"'.,;:!?()[\]{}]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

function scoreAnswer(draft: string, answer: string) {
  const normalizedDraft = normalizeAnswer(draft)
  const normalizedAnswer = normalizeAnswer(answer)
  if (!normalizedDraft) return 'empty' as const
  if (normalizedDraft === normalizedAnswer) return 'correct' as const
  if (
    normalizedDraft.length >= 3
    && (normalizedAnswer.includes(normalizedDraft) || normalizedDraft.includes(normalizedAnswer))
  ) {
    return 'close' as const
  }
  return 'miss' as const
}

function parseChoiceLines(value: string) {
  return value
    .split('\n')
    .map((line) => line.replace(/^\s*(?:[-*]\s*)?(?:[A-Ha-h][.)]\s*)?/, '').trim())
    .filter(Boolean)
}

function completeProgress(current: BiteProgress) {
  const today = todayKey()
  if (current.lastCompletedDate === today) return current
  const streak = current.lastCompletedDate === yesterdayKey() ? current.streak + 1 : 1
  return {
    version: 1,
    streak,
    lastCompletedDate: today,
    completedCount: current.completedCount + 1,
  } satisfies BiteProgress
}

function draftFromBite(bite: DailyBite): BiteCardPayload {
  return {
    source_type: bite.source_type,
    source_id: bite.source_id,
    title: bite.title,
    area: bite.area,
    difficulty: bite.difficulty,
    question_type: bite.question_type,
    prompt: bite.prompt,
    answer: bite.answer,
    options: bite.options,
    hint: bite.hint,
    explanation: bite.explanation,
    status: 'active',
  }
}

export function DailyBitePanel({
  onOpenQuiz,
  onOpenNode,
}: {
  onOpenQuiz: (quizId: string) => void
  onOpenNode: (slug: string) => void
}) {
  const [bite, setBite] = useState<DailyBite | null>(null)
  const [cursor, setCursor] = useState('')
  const [answerDraft, setAnswerDraft] = useState('')
  const [isHintVisible, setIsHintVisible] = useState(false)
  const [isRevealed, setIsRevealed] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState('')
  const [progress, setProgress] = useState<BiteProgress>(() => readProgress())
  const [customBites, setCustomBites] = useState<DailyBite[]>([])
  const [editDraft, setEditDraft] = useState<BiteCardPayload | null>(null)
  const [editNotice, setEditNotice] = useState('')
  const [choiceDraft, setChoiceDraft] = useState('')

  const answerState = useMemo(
    () => (bite ? scoreAnswer(answerDraft, bite.answer) : 'empty'),
    [answerDraft, bite],
  )

  const applyBiteResponse = useCallback((data: ApiBiteResponse) => {
    setBite(data.bite)
    setCursor(data.next_cursor)
    setAnswerDraft('')
    setIsHintVisible(false)
    setIsRevealed(false)
    setEditDraft(null)
    setEditNotice('')
    setChoiceDraft('')
  }, [])

  const loadCustomBites = useCallback(async () => {
    const data = await fetchJson<ApiBitesResponse>('/api/bites')
    setCustomBites(data.bites)
  }, [])

  const loadDailyBite = useCallback(async () => {
    try {
      setIsLoading(true)
      setError('')
      const [dailyData, customData] = await Promise.all([
        fetchJson<ApiBiteResponse>('/api/bite/daily'),
        fetchJson<ApiBitesResponse>('/api/bites'),
      ])
      applyBiteResponse(dailyData)
      setCustomBites(customData.bites)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Unable to load Daily Bite')
    } finally {
      setIsLoading(false)
    }
  }, [applyBiteResponse])

  const loadNextBite = async () => {
    try {
      setIsLoading(true)
      setError('')
      applyBiteResponse(await fetchJson<ApiBiteResponse>(`/api/bite/next?cursor=${encodeURIComponent(cursor)}`))
      await loadCustomBites()
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Unable to load Daily Bite')
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    const timer = window.setTimeout(() => {
      loadDailyBite()
    }, 0)
    return () => window.clearTimeout(timer)
  }, [loadDailyBite])

  const reveal = () => {
    if (!answerDraft.trim()) return
    setIsRevealed(true)
    setProgress((current) => {
      const next = completeProgress(current)
      writeProgress(next)
      return next
    })
  }

  const saveCustomBite = async () => {
    if (!bite) return
    try {
      setIsSaving(true)
      setEditNotice('')
      const payload = editDraft ?? draftFromBite(bite)
      const response = bite.card_id
        ? await putJson<ApiBiteCardResponse>(`/api/bites/${bite.card_id}`, payload)
        : await postJson<ApiBiteCardResponse>('/api/bites', payload)
      setBite(response.bite)
      setCursor(response.bite.id)
      setEditDraft(null)
      setEditNotice(bite.card_id ? 'Bite updated.' : 'Saved as custom Bite.')
      await loadCustomBites()
    } catch (saveError) {
      setEditNotice(saveError instanceof Error ? saveError.message : 'Unable to save Bite')
    } finally {
      setIsSaving(false)
    }
  }

  const deleteCustomBite = async () => {
    if (!bite?.card_id) return
    try {
      setIsSaving(true)
      await deleteJson<ApiBiteCardResponse>(`/api/bites/${bite.card_id}`)
      setEditNotice('Bite archived.')
      await loadDailyBite()
    } catch (deleteError) {
      setEditNotice(deleteError instanceof Error ? deleteError.message : 'Unable to archive Bite')
    } finally {
      setIsSaving(false)
    }
  }

  const openCustomBite = (nextBite: DailyBite) => {
    setBite(nextBite)
    setCursor(nextBite.id)
    setAnswerDraft('')
    setIsHintVisible(false)
    setIsRevealed(false)
    setEditDraft(null)
    setEditNotice('')
    setChoiceDraft('')
  }

  const openEditDraft = () => {
    if (!bite) return
    const draft = draftFromBite(bite)
    setEditDraft(draft)
    setChoiceDraft(draft.options.join('\n'))
  }

  const updateEditDraft = (patch: Partial<BiteCardPayload>) => {
    setEditDraft((current) => (current ? { ...current, ...patch } : current))
  }

  const updateChoiceDraft = (value: string) => {
    setChoiceDraft(value)
    updateEditDraft({ options: parseChoiceLines(value) })
  }

  const toggleQuestionType = (questionType: 'blank' | 'multiple_choice') => {
    if (!editDraft) return
    const options = questionType === 'multiple_choice'
      ? editDraft.options.length ? editDraft.options : parseChoiceLines(choiceDraft)
      : []
    if (questionType === 'multiple_choice' && !choiceDraft) {
      setChoiceDraft(options.join('\n'))
    }
    updateEditDraft({ question_type: questionType, options })
  }

  return (
    <section className="daily-bite-panel" aria-label="Daily Bite">
      <div className="bite-topline">
        <div>
          <p className="eyebrow">Daily Bite</p>
          <h2>{bite?.title ?? 'Micro drill'}</h2>
        </div>
        <div className="bite-streak" aria-label="Daily Bite streak">
          <span>{progress.streak}</span>
          <strong>streak</strong>
        </div>
      </div>

      {error && <p className="error-banner">{error}</p>}
      {isLoading && !bite ? (
        <p className="detail-loading">Loading bite...</p>
      ) : bite ? (
        <>
          <div className="bite-meta-row">
            <span>{bite.area}</span>
            <span>{bite.difficulty}</span>
            <span>{bite.question_type === 'multiple_choice' ? 'choice' : 'blank'}</span>
            <span>{bite.card_id ? 'custom' : 'generated'}</span>
            <span>{progress.completedCount} completed</span>
          </div>

          <article className="bite-question-card">
            <p>{bite.prompt}</p>
            {bite.question_type === 'multiple_choice' ? (
              <div className="bite-choice-list" role="radiogroup" aria-label="Daily Bite choices">
                {bite.options.map((option) => (
                  <button
                    key={option}
                    type="button"
                    className={answerDraft === option ? 'selected' : ''}
                    role="radio"
                    aria-checked={answerDraft === option}
                    onClick={() => setAnswerDraft(option)}
                  >
                    {option}
                  </button>
                ))}
              </div>
            ) : (
              <input
                value={answerDraft}
                onChange={(event) => setAnswerDraft(event.target.value)}
                placeholder="Type the missing answer"
                aria-label="Daily Bite answer"
                onKeyDown={(event) => {
                  if (event.key === 'Enter') reveal()
                }}
              />
            )}
            <div className="bite-actions">
              <button
                type="button"
                className="focus-toggle"
                onClick={() => setIsHintVisible((current) => !current)}
              >
                {isHintVisible ? 'Hide hint' : 'Hint'}
              </button>
              <button type="button" className="focus-toggle ai-action" disabled={!answerDraft.trim()} onClick={reveal}>
                Check
              </button>
              <button type="button" className="focus-toggle" disabled={isLoading} onClick={loadNextBite}>
                Next
              </button>
            </div>
          </article>

          {isHintVisible && (
            <section className="bite-note">
              <h3>Hint</h3>
              <p>{bite.hint}</p>
            </section>
          )}

          {isRevealed && (
            <section className={`bite-answer ${answerState}`}>
              <p className="eyebrow">
                {answerState === 'correct' ? 'Correct' : answerState === 'close' ? 'Close' : 'Answer'}
              </p>
              <strong>{bite.answer}</strong>
              <ol>
                {bite.explanation.map((line) => (
                  <li key={line}>{line}</li>
                ))}
              </ol>
            </section>
          )}

          <div className="bite-link-row">
            {bite.source_type === 'quiz' ? (
              <button type="button" className="text-link" onClick={() => onOpenQuiz(bite.source_id)}>
                Open full quiz
              </button>
            ) : (
              <button type="button" className="text-link" onClick={() => onOpenNode(bite.source_id)}>
                Open source node
              </button>
            )}
            {bite.linked_nodes[0] && (
              <button type="button" className="text-link" onClick={() => onOpenNode(bite.linked_nodes[0].slug)}>
                Open node
              </button>
            )}
          </div>

          <section className="bite-crud-panel" aria-label="Daily Bite CRUD">
            <div className="bite-crud-head">
              <div>
                <p className="eyebrow">Custom Bite</p>
                <h3>{bite.card_id ? 'Edit lightweight card' : 'Save this projection'}</h3>
              </div>
              <div className="bite-actions">
                <button
                  type="button"
                  className="focus-toggle"
                  onClick={() => {
                    if (editDraft) {
                      setEditDraft(null)
                      setChoiceDraft('')
                    } else {
                      openEditDraft()
                    }
                  }}
                >
                  {editDraft ? 'Close editor' : bite.card_id ? 'Edit bite' : 'Save/edit'}
                </button>
                {bite.card_id && (
                  <button type="button" className="focus-toggle danger-button" disabled={isSaving} onClick={deleteCustomBite}>
                    Archive
                  </button>
                )}
              </div>
            </div>
            {editDraft && (
              <div className="bite-edit-grid">
                <div className="bite-type-toggle" aria-label="Bite type">
                  <button
                    type="button"
                    className={editDraft.question_type === 'blank' ? 'active' : ''}
                    onClick={() => toggleQuestionType('blank')}
                  >
                    Fill blank
                  </button>
                  <button
                    type="button"
                    className={editDraft.question_type === 'multiple_choice' ? 'active' : ''}
                    onClick={() => toggleQuestionType('multiple_choice')}
                  >
                    Multiple choice
                  </button>
                </div>
                <input
                  value={editDraft.title}
                  onChange={(event) => updateEditDraft({ title: event.target.value })}
                  aria-label="Bite title"
                  placeholder="Title"
                />
                <input
                  value={editDraft.prompt}
                  onChange={(event) => updateEditDraft({ prompt: event.target.value })}
                  aria-label="Bite prompt"
                  placeholder={editDraft.question_type === 'multiple_choice' ? 'Question prompt' : 'Prompt with ____'}
                />
                <input
                  value={editDraft.answer}
                  onChange={(event) => updateEditDraft({ answer: event.target.value })}
                  aria-label="Bite answer"
                  placeholder="Answer"
                />
                {editDraft.question_type === 'multiple_choice' && (
                  <textarea
                    value={choiceDraft}
                    onChange={(event) => updateChoiceDraft(event.target.value)}
                    aria-label="Bite options"
                    placeholder="Choices, one per line. Include the answer."
                  />
                )}
                <input
                  value={editDraft.hint}
                  onChange={(event) => updateEditDraft({ hint: event.target.value })}
                  aria-label="Bite hint"
                  placeholder="Hint"
                />
                <textarea
                  value={editDraft.explanation.join('\n')}
                  onChange={(event) =>
                    updateEditDraft({ explanation: event.target.value.split('\n').filter((line) => line.trim()) })
                  }
                  aria-label="Bite explanation"
                  placeholder="Three short explanation lines"
                />
                <button type="button" className="focus-toggle ai-action" disabled={isSaving} onClick={saveCustomBite}>
                  {isSaving ? 'Saving...' : 'Save Bite'}
                </button>
              </div>
            )}
            {editNotice && <p className={editNotice.includes('Request failed') ? 'inline-error' : 'inline-hint'}>{editNotice}</p>}
          </section>

          {customBites.length > 0 && (
            <section className="bite-saved-list" aria-label="Saved Daily Bites">
              <p className="eyebrow">Saved bites</p>
              <div>
                {customBites.slice(0, 6).map((customBite) => (
                  <button
                    key={customBite.id}
                    type="button"
                    className={customBite.id === bite.id ? 'active' : ''}
                    onClick={() => openCustomBite(customBite)}
                  >
                    {customBite.title}
                  </button>
                ))}
              </div>
            </section>
          )}
        </>
      ) : (
        <div className="empty-state">
          <h2>No bite available</h2>
          <p>Add a quiz Markdown file to enable Daily Bite.</p>
        </div>
      )}
    </section>
  )
}
