import { clearAuthSession, readAuthToken } from '@/lib/auth-storage'
import { type BackendErrorResponse } from '@/types/api'

const rawApiBase = import.meta.env.VITE_API_BASE_URL ?? ''
const apiBase = rawApiBase.replace(/\/$/, '')

export class ApiError extends Error {
  status: number
  details?: BackendErrorResponse

  constructor(message: string, status: number, details?: BackendErrorResponse) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.details = details
  }
}

interface RequestOptions {
  auth?: boolean
}

const resolvePath = (path: string): string => {
  if (path.startsWith('http://') || path.startsWith('https://')) {
    return path
  }

  if (!path.startsWith('/')) {
    return `${apiBase}/${path}`
  }

  return `${apiBase}${path}`
}

const redirectToLoginIfNeeded = () => {
  clearAuthSession()
  if (window.location.pathname !== '/login') {
    window.location.assign('/login')
  }
}

const parseError = async (response: Response): Promise<ApiError> => {
  let details: BackendErrorResponse | undefined

  try {
    details = (await response.json()) as BackendErrorResponse
  } catch {
    details = undefined
  }

  const message = details?.message ?? `Request failed with status ${response.status}`
  return new ApiError(message, response.status, details)
}

export const apiRequest = async <T>(
  path: string,
  init?: RequestInit,
  options: RequestOptions = { auth: true },
): Promise<T> => {
  const headers = new Headers(init?.headers ?? {})

  if (options.auth !== false) {
    const token = readAuthToken()
    if (!token) {
      throw new ApiError('Authentication is required', 401)
    }
    headers.set('Authorization', `Bearer ${token}`)
  }

  if (!headers.has('Content-Type') && init?.body) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(resolvePath(path), {
    ...init,
    headers,
  })

  if (!response.ok) {
    const error = await parseError(response)
    if (error.status === 401) {
      redirectToLoginIfNeeded()
    }
    throw error
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}
