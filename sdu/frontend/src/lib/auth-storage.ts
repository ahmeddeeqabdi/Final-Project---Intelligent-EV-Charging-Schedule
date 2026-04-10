import { type AuthUser } from '@/types/api'

export const AUTH_TOKEN_STORAGE_KEY = 'ev-auth-token'
export const AUTH_USER_STORAGE_KEY = 'ev-auth-user'

export const readAuthToken = (): string | null => {
  return window.localStorage.getItem(AUTH_TOKEN_STORAGE_KEY)
}

export const readAuthUser = (): AuthUser | null => {
  const raw = window.localStorage.getItem(AUTH_USER_STORAGE_KEY)
  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw) as AuthUser
  } catch {
    return null
  }
}

export const persistAuthSession = (token: string, user: AuthUser) => {
  window.localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, token)
  window.localStorage.setItem(AUTH_USER_STORAGE_KEY, JSON.stringify(user))
}

export const clearAuthSession = () => {
  window.localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY)
  window.localStorage.removeItem(AUTH_USER_STORAGE_KEY)
}
