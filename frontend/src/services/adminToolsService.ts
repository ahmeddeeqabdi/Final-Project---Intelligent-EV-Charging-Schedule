import { apiRequest } from '@/services/apiClient'
import { type AdminBenchmarkResponse } from '@/types/api'

interface BenchmarkRequest {
  scenarios?: number
  seed?: number
}

export const runAdminBenchmark = async (payload: BenchmarkRequest): Promise<AdminBenchmarkResponse> => {
  return apiRequest<AdminBenchmarkResponse>('/api/admin/benchmarks/run', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export const runAdminDataSync = async (date: string, zone?: 'DK1' | 'DK2'): Promise<string> => {
  const query = new URLSearchParams({ date })
  if (zone) {
    query.set('zone', zone)
  }

  return apiRequest<string>(`/api/v1/schedule/sync?${query.toString()}`, {
    method: 'POST',
  })
}
