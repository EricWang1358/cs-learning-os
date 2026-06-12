export type EditDraftTargetType = 'node' | 'quiz'

export type StoredEditDraft = {
  version: 1
  targetType: EditDraftTargetType
  targetId: string
  bodyHash: string
  body: string
  savedAt: string
}

const EDIT_DRAFT_STORAGE_PREFIX = 'cs-learning-os:edit-draft:v1'

function editDraftStorageKey(targetType: EditDraftTargetType, targetId: string) {
  return `${EDIT_DRAFT_STORAGE_PREFIX}:${targetType}:${encodeURIComponent(targetId)}`
}

export function readStoredEditDraft(
  targetType: EditDraftTargetType,
  targetId: string,
  bodyHash: string,
): StoredEditDraft | null {
  try {
    const raw = window.localStorage.getItem(editDraftStorageKey(targetType, targetId))
    if (!raw) return null
    const parsed = JSON.parse(raw) as StoredEditDraft
    if (
      parsed.version !== 1 ||
      parsed.targetType !== targetType ||
      parsed.targetId !== targetId ||
      parsed.bodyHash !== bodyHash ||
      !parsed.body.trim()
    ) {
      return null
    }
    return parsed
  } catch {
    return null
  }
}

export function writeStoredEditDraft(
  targetType: EditDraftTargetType,
  targetId: string,
  bodyHash: string,
  body: string,
) {
  const key = editDraftStorageKey(targetType, targetId)
  try {
    if (!body.trim()) {
      window.localStorage.removeItem(key)
      return
    }
    const payload: StoredEditDraft = {
      version: 1,
      targetType,
      targetId,
      bodyHash,
      body,
      savedAt: new Date().toISOString(),
    }
    window.localStorage.setItem(key, JSON.stringify(payload))
  } catch {
    // Draft autosave is best-effort; canonical saves still go through the backend.
  }
}

export function clearStoredEditDraft(targetType: EditDraftTargetType, targetId: string) {
  try {
    window.localStorage.removeItem(editDraftStorageKey(targetType, targetId))
  } catch {
    // Ignore storage cleanup failures.
  }
}
