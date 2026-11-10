import { describe, expect, it } from 'vitest'
import { formatCo2, formatDuration, formatRelative } from './format'

describe('format helpers', () => {
  it('switches CO2 to kilograms above one kilogram', () => {
    expect(formatCo2(850)).toBe('850 g CO₂')
    expect(formatCo2(5_814)).toBe('5.8 kg CO₂')
  })

  it('formats durations compactly', () => {
    expect(formatDuration(0)).toBe('0 min')
    expect(formatDuration(0.5)).toBe('30 min')
    expect(formatDuration(3)).toBe('3 h')
    expect(formatDuration(3.85)).toBe('3 h 51 min')
    expect(formatDuration(Infinity)).toBe('0 min')
  })

  it('describes past timestamps relatively', () => {
    const now = Date.UTC(2026, 10, 10, 12)
    expect(formatRelative(now - 26 * 3_600_000, now)).toBe('yesterday')
    expect(formatRelative(now - 3 * 3_600_000, now)).toBe('3 hours ago')
  })
})
