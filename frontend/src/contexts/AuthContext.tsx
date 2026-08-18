import { useMemo, useState, type ReactNode } from 'react'
import { clearAuthSession, persistAuthSession, readAuthToken, readAuthUser } from '@/lib/auth-storage'
import { loginUser, signupUser } from '@/services/authService'
import { type AuthContextValue, type LoginRequest, type SignupRequest } from '@/types/api'
import { AuthContext } from '@/contexts/auth-context'

interface AuthState {
  token: string | null
  user: AuthContextValue['user']
}

interface AuthProviderProps {
  children: ReactNode
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [state, setState] = useState<AuthState>(() => ({
    token: readAuthToken(),
    user: readAuthUser(),
  }))

  const login = async (payload: LoginRequest) => {
    const response = await loginUser(payload)
    persistAuthSession(response.token, response.user)
    setState({ token: response.token, user: response.user })
  }

  const signup = async (payload: SignupRequest) => {
    const response = await signupUser(payload)
    persistAuthSession(response.token, response.user)
    setState({ token: response.token, user: response.user })
  }

  const logout = () => {
    clearAuthSession()
    setState({ token: null, user: null })
  }

  const value = useMemo<AuthContextValue>(
    () => ({
      token: state.token,
      user: state.user,
      isAuthenticated: Boolean(state.token && state.user),
      login,
      signup,
      logout,
    }),
    [state.token, state.user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
