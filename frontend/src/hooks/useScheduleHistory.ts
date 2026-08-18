import { useQuery } from '@tanstack/react-query'
import { mapBackendToUi } from '@/hooks/useSchedule'
import { apiRequest } from '@/services/apiClient'
import type { BackendScheduleHistoryItem, ScheduleHistoryItem } from '@/types/api'

export const useScheduleHistory = () =>
  useQuery<ScheduleHistoryItem[]>({
    queryKey: ['schedule-history'],
    queryFn: async () => {
      const items = await apiRequest<BackendScheduleHistoryItem[]>('/api/v1/schedule/history')
      return items.map((item) => ({
        ...mapBackendToUi(
          {
            slots: item.slots,
            totalPredictedCost: item.totalPredictedCost,
            totalPredictedEmissions: item.totalPredictedEmissions,
            degradedMode: item.degradedMode,
            marketSignals: [],
          },
          item.algorithm,
        ),
        id: item.id,
        createdAt: item.createdAt,
      }))
    },
  })
