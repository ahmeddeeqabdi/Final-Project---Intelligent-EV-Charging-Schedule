import { apiRequest } from '@/services/apiClient'
import { type AuthResponse, type LoginRequest, type SignupRequest } from '@/types/api'

export const signupUser = async (payload: SignupRequest): Promise<AuthResponse> => {
  return apiRequest<AuthResponse>(
    '/api/auth/signup',
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
    { auth: false },
  )
}

export const loginUser = async (payload: LoginRequest): Promise<AuthResponse> => {
  return apiRequest<AuthResponse>(
    '/api/auth/login',
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
    { auth: false },
  )
}
