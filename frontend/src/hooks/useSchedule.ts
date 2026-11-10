import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError, apiRequest } from '@/services/apiClient'
import {
  type BackendScheduleRequest,
  type BackendScheduleResult,
  type OptimizationAlgorithm,
  type ScheduleFormValues,
  type ScheduleRunResult,
  type ScheduleResult,
} from '@/types/api'

/** Normalises every scheduling failure (HTTP, network, validation) into an ApiError with a status. */
const toApiError = (caught: unknown): ApiError => {
  if (caught instanceof ApiError) return caught
  if (caught instanceof TypeError) {
    return new ApiError('Cannot reach the scheduling service. Check that the backend is running and try again.', 503)
  }
  return new ApiError(caught instanceof Error ? caught.message : 'Scheduling request failed. Please retry in a moment.', 500)
}

const mapFormToRequest = (values: ScheduleFormValues): BackendScheduleRequest => {
  const departureDate = new Date(values.departureTime)

  if (Number.isNaN(departureDate.getTime())) {
    throw new ApiError('Invalid departure time format.', 400)
  }

  return {
    currentSocPercent: values.currentSoC,
    targetSocPercent: values.targetSoC,
    batteryCapacityKwh: values.batteryCapacity,
    maxChargingPowerKw: values.maxPower,
    plugInTime: new Date().toISOString(),
    departureTime: departureDate.toISOString(),
    priceZone: values.priceZone,
    weightPrice: values.costWeight,
    weightCO2: 1 - values.costWeight,
  }
}

export const mapBackendToUi = (
  payload: BackendScheduleResult,
  algorithm: OptimizationAlgorithm,
): ScheduleResult => {
  const degradedMode = payload.degradedMode ?? {
    enabled: false,
    source: 'live',
    reason: null,
  }

  return {
    algorithm,
    totalCost: payload.totalPredictedCost,
    totalCO2: payload.totalPredictedEmissions,
    isDegradedMode: degradedMode.enabled,
    fallbackSource: degradedMode.source,
    fallbackReason: degradedMode.reason ?? undefined,
    fallbackDataAgeHours: degradedMode.dataAgeHours ?? undefined,
    slots: payload.slots.map((slot) => ({
      timestamp: slot.timestamp,
      powerValue: slot.powerDraw,
      energyPrice: slot.currentPrice,
      co2Intensity: slot.currentCO2,
    })),
    marketSignals: (payload.marketSignals ?? []).map((point) => ({
      timestamp: point.timestamp,
      energyPrice: point.energyPrice,
      co2Intensity: point.co2Intensity,
    })),
  }
}

export const submitSchedule = async (
  values: ScheduleFormValues,
  algorithm: OptimizationAlgorithm,
  persist: boolean,
): Promise<ScheduleResult> => {
  try {
    const search = new URLSearchParams({ algorithm, persist: String(persist) })
    const payload = await apiRequest<BackendScheduleResult>(`/api/v1/schedule?${search.toString()}`, {
      method: 'POST',
      body: JSON.stringify(mapFormToRequest(values)),
    })
    return mapBackendToUi(payload, algorithm)
  } catch (caught) {
    throw toApiError(caught)
  }
}

export const useSchedule = () => {
  const queryClient = useQueryClient()

  return useMutation<ScheduleRunResult, ApiError, ScheduleFormValues>({
    mutationFn: async (values) => {
      const algorithms: OptimizationAlgorithm[] = values.compareStrategies
        ? ['naive', 'greedy', 'optimal', 'mip']
        : Array.from(new Set<OptimizationAlgorithm>([values.algorithm, 'naive']))

      // Build the requested plan first. This both gives the user-facing result
      // priority and lets any on-demand market-data refresh complete before the
      // comparison requests start, avoiding duplicate concurrent data inserts.
      const selected = await submitSchedule(values, values.algorithm, true)
      const alternatives = algorithms.filter((algorithm) => algorithm !== values.algorithm)
      const attempts = await Promise.allSettled(
        alternatives.map((algorithm) => submitSchedule(values, algorithm, false)),
      )
      const results = attempts.flatMap((attempt) => attempt.status === 'fulfilled' ? [attempt.value] : [])
      const comparisons: Partial<Record<OptimizationAlgorithm, ScheduleResult>> = {
        ...Object.fromEntries(results.map((result) => [result.algorithm, result])),
        [selected.algorithm]: selected,
      }

      return { selected, comparisons }
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['schedule-history'] })
    },
  })
}
