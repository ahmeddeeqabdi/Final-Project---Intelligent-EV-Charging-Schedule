import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/hooks/useAuth'
import { getMyConstraints, updateMyConstraints } from '@/services/userConstraintsService'
import { type UpdateUserConstraintsRequest } from '@/types/api'

export const useUserConstraints = () => {
  const queryClient = useQueryClient()
  const { isAuthenticated } = useAuth()

  const query = useQuery({
    queryKey: ['user-constraints'],
    queryFn: getMyConstraints,
    enabled: isAuthenticated,
  })

  const updateMutation = useMutation({
    mutationFn: (payload: UpdateUserConstraintsRequest) => updateMyConstraints(payload),
    onSuccess: (data) => {
      queryClient.setQueryData(['user-constraints'], data)
    },
  })

  return {
    query,
    updateMutation,
  }
}
