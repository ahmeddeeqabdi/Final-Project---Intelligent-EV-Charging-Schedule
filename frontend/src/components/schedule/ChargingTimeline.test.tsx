import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { ScheduleResult } from '@/types/api'
import { ChargingTimeline } from './ChargingTimeline'

const hour = (offset: number) => new Date(Date.UTC(2026, 10, 10, 16 + offset)).toISOString()
const prices = [2.4, 2.1, 0.8, 0.7]

const result: ScheduleResult = {
  algorithm: 'optimal',
  totalCost: 16.5,
  totalCO2: 2_000,
  isDegradedMode: false,
  fallbackSource: 'live',
  slots: prices.map((price, index) => ({ timestamp: hour(index), powerValue: index >= 2 ? 11 : 0, energyPrice: price, co2Intensity: 100 })),
  marketSignals: prices.map((price, index) => ({ timestamp: hour(index), energyPrice: price, co2Intensity: 100 })),
}

describe('ChargingTimeline', () => {
  it('summarises charging hours and the average price paid', () => {
    render(<ChargingTimeline result={result} />)

    expect(screen.getByRole('img', { name: /2 charging hours/ })).toBeInTheDocument()
    expect(screen.getByText('0.75 kr/kWh')).toBeInTheDocument()
    expect(screen.getByText('1.50 kr/kWh')).toBeInTheDocument()
  })

  it('shows details for a tapped hour', async () => {
    const { container } = render(<ChargingTimeline result={result} />)
    const bars = container.querySelectorAll('[aria-hidden="true"] > .group')

    await userEvent.setup().click(bars[2])

    expect(screen.getByText(/charging 11\.0 kW · 0\.80 kr\/kWh/)).toBeInTheDocument()
  })
})
