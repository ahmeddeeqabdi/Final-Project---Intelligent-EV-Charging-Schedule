import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError, apiRequest } from '@/services/apiClient'
import {
  type BackendErrorResponse,
  type BackendScheduleRequest,
  type BackendScheduleResult,
  type OptimizationAlgorithm,
  type ScheduleFormValues,
  type ScheduleRunResult,
  type ScheduleResult,
} from '@/types/api'

export class ScheduleApiError extends Error {
  status: number
  details?: BackendErrorResponse

  constructor(message: string, status: number, details?: BackendErrorResponse) {
    super(message)
    this.name = 'ScheduleApiError'
    this.status = status
    this.details = details
  }
}

const mapFormToRequest = (values: ScheduleFormValues): BackendScheduleRequest => {
  const departureDate = new Date(values.departureTime)

  if (Number.isNaN(departureDate.getTime())) {
    throw new ScheduleApiError('Invalid departure time format.', 400)
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
  const requestPayload = mapFormToRequest(values)
  const search = new URLSearchParams({ algorithm, persist: String(persist) })

  try {
    const payload = await apiRequest<BackendScheduleResult>(`/api/v1/schedule?${search.toString()}`, {
      method: 'POST',
      body: JSON.stringify(requestPayload),
    })

    return mapBackendToUi(payload, algorithm)
  } catch (caught) {
    if (caught instanceof ScheduleApiError) {
      throw caught
    }

    if (caught instanceof ApiError) {
      throw new ScheduleApiError(caught.message, caught.status, caught.details)
    }

    if (caught instanceof TypeError) {
      throw new ScheduleApiError(
        'Cannot reach scheduling API. Confirm backend is running and frontend is using the local proxy.',
        503,
      )
    }

    if (caught instanceof Error) {
      throw new ScheduleApiError(caught.message, 500)
    }

    throw new ScheduleApiError('Scheduling request failed. Please retry in a moment.', 500)
  }
}

export const useSchedule = () => {
  const queryClient = useQueryClient()

  return useMutation<ScheduleRunResult, ScheduleApiError, ScheduleFormValues>({
    mutationFn: async (values) => {
      const algorithms: OptimizationAlgorithm[] = values.compareStrategies
        ? ['naive', 'greedy', 'optimal', 'mip']
        : Array.from(new Set<OptimizationAlgorithm>([values.algorithm, 'naive']))

      const attempts = await Promise.allSettled(
        algorithms.map((algorithm) =>
          submitSchedule(values, algorithm, algorithm === values.algorithm),
        ),
      )
      const results = attempts.flatMap((attempt) => attempt.status === 'fulfilled' ? [attempt.value] : [])
      const comparisons = Object.fromEntries(results.map((result) => [result.algorithm, result]))
      const selected = comparisons[values.algorithm]

      if (!selected) {
        const selectedAttempt = attempts[algorithms.indexOf(values.algorithm)]
        if (selectedAttempt?.status === 'rejected' && selectedAttempt.reason instanceof ScheduleApiError) {
          throw selectedAttempt.reason
        }
        throw new ScheduleApiError('The selected strategy did not return a result.', 500)
      }

      return { selected, comparisons }
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['schedule-history'] })
    },
  })
}
