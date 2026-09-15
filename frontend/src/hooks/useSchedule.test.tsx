import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/services/apiClient'
import type { BackendScheduleResult, ScheduleFormValues } from '@/types/api'
import { useSchedule } from './useSchedule'

vi.mock('@/services/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/services/apiClient')>()
  return { ...actual, apiRequest: vi.fn() }
})

const backendResult: BackendScheduleResult = {
  slots: [],
  totalPredictedCost: 10,
  totalPredictedEmissions: 1_000,
  degradedMode: {
    enabled: false,
    reason: null,
    source: 'live',
    dataAgeHours: null,
  },
  marketSignals: [],
}

const values: ScheduleFormValues = {
  currentSoC: 25,
  targetSoC: 80,
  batteryCapacity: 77,
  maxPower: 11,
  departureTime: '2026-09-16T07:00',
  priceZone: 'DK2',
  algorithm: 'optimal',
  costWeight: 0.5,
  compareStrategies: true,
}

describe('useSchedule', () => {
  beforeEach(() => {
    vi.mocked(apiRequest).mockReset()
    vi.mocked(apiRequest).mockResolvedValue(backendResult as never)
  })

  it('builds the selected strategy before requesting comparisons', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const wrapper = ({ children }: PropsWithChildren) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )

    const { result } = renderHook(() => useSchedule(), { wrapper })
    const schedule = await result.current.mutateAsync(values)

    const paths = vi.mocked(apiRequest).mock.calls.map(([path]) => String(path))
    expect(paths[0]).toContain('algorithm=optimal')
    expect(paths[0]).toContain('persist=true')
    expect(paths.slice(1)).toEqual(expect.arrayContaining([
      expect.stringContaining('algorithm=naive'),
      expect.stringContaining('algorithm=greedy'),
      expect.stringContaining('algorithm=mip'),
    ]))
    expect(paths.slice(1)).toEqual(expect.not.arrayContaining([
      expect.stringContaining('persist=true'),
    ]))
    expect(schedule.selected.algorithm).toBe('optimal')
    expect(schedule.comparisons).toHaveProperty('optimal')
  })
})
