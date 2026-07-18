import type { ApiErrorBody } from '../types/api'

export const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://127.0.0.1:8000'

export class ApiRequestError extends Error {
  status: number
  body: unknown

  constructor(status: number, message: string, body: unknown = null) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
    this.body = body
  }
}

export async function fetchJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`)
  if (!response.ok) {
    throw new ApiRequestError(response.status, await responseErrorMessage(response))
  }
  return response.json() as Promise<T>
}

export async function postJson<T>(path: string, payload: unknown): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!response.ok) {
    throw new ApiRequestError(response.status, await responseErrorMessage(response))
  }
  return response.json() as Promise<T>
}

export async function putJson<T>(path: string, payload: unknown): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!response.ok) {
    throw new ApiRequestError(response.status, await responseErrorMessage(response))
  }
  return response.json() as Promise<T>
}

export async function patchJson<T>(path: string, payload: unknown): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new ApiRequestError(response.status, responseErrorMessageFromBody(response.status, response.statusText, body), body)
  }
  return response.json() as Promise<T>
}

export async function deleteJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'DELETE',
  })
  if (!response.ok) {
    throw new ApiRequestError(response.status, await responseErrorMessage(response))
  }
  return response.json() as Promise<T>
}

async function responseErrorMessage(response: Response) {
  try {
    const body = (await response.json()) as ApiErrorBody
    return responseErrorMessageFromBody(response.status, response.statusText, body)
  } catch {
    // Fall through to the generic message when the backend did not return JSON.
  }
  return `Request failed: ${response.status} ${response.statusText}`.trim()
}

function responseErrorMessageFromBody(status: number, statusText: string, body: unknown) {
  const detail = (body as ApiErrorBody | null)?.detail
  if (detail) {
    if (Array.isArray(detail)) {
      const details = detail
        .map((item) => {
          const field = item.loc?.filter((part) => part !== 'body').join('.') || 'request'
          return `${field}: ${item.msg || item.type || 'invalid value'}`
        })
        .join('; ')
      return `Request failed ${status}: ${details}`
    }
    if (typeof detail === 'object') {
      const message = typeof detail.message === 'string' ? detail.message : typeof detail.code === 'string' ? detail.code : JSON.stringify(detail)
      return `Request failed ${status}: ${message}`
    }
    return `Request failed ${status}: ${String(detail)}`
  }
  return `Request failed: ${status} ${statusText}`.trim()
}
