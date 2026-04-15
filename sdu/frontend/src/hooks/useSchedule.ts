import { useMutation } from '@tanstack/react-query'
import { ApiError, apiRequest } from '@/services/apiClient'
import {
  type BackendErrorResponse,
  type BackendScheduleRequest,
  type BackendScheduleResult,
  type ScheduleFormValues,
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

const mapBackendToUi = (payload: BackendScheduleResult): ScheduleResult => {
  const degradedMode = payload.degradedMode ?? {
    enabled: false,
    source: 'live',
    reason: null,
  }

  return {
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
  }
}

const submitSchedule = async (values: ScheduleFormValues): Promise<ScheduleResult> => {
  const requestPayload = mapFormToRequest(values)
  const search = new URLSearchParams({ algorithm: values.algorithm })

  try {
    const payload = await apiRequest<BackendScheduleResult>(`/api/v1/schedule?${search.toString()}`, {
      method: 'POST',
      body: JSON.stringify(requestPayload),
    })

    return mapBackendToUi(payload)
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
  return useMutation<ScheduleResult, ScheduleApiError, ScheduleFormValues>({
    mutationFn: submitSchedule,
  })
}
