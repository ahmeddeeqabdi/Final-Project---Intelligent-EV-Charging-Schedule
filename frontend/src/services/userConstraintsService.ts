import { apiRequest } from '@/services/apiClient'
import { type UpdateUserConstraintsRequest, type UserConstraints } from '@/types/api'

export const getMyConstraints = async (): Promise<UserConstraints> => {
  return apiRequest<UserConstraints>('/api/user/me/constraints')
}

export const updateMyConstraints = async (payload: UpdateUserConstraintsRequest): Promise<UserConstraints> => {
  return apiRequest<UserConstraints>('/api/user/me/constraints', {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}
