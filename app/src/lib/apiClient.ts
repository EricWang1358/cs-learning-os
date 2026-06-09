import type { ApiErrorBody } from '../types/api'

export const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://127.0.0.1:8000'

export class ApiRequestError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
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
    if (body.detail) {
      if (Array.isArray(body.detail)) {
        const details = body.detail
          .map((item) => {
            const field = item.loc?.filter((part) => part !== 'body').join('.') || 'request'
            return `${field}: ${item.msg || item.type || 'invalid value'}`
          })
          .join('; ')
        return `Request failed ${response.status}: ${details}`
      }
      return `Request failed ${response.status}: ${body.detail}`
    }
  } catch {
    // Fall through to the generic message when the backend did not return JSON.
  }
  return `Request failed: ${response.status} ${response.statusText}`.trim()
}
