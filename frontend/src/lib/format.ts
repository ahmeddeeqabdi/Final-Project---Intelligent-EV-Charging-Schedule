import type { OptimizationAlgorithm } from '@/types/api'

const currency = new Intl.NumberFormat('en-DK', { style: 'currency', currency: 'DKK', minimumFractionDigits: 2, maximumFractionDigits: 2 })
const oneDecimal = new Intl.NumberFormat('en-GB', { minimumFractionDigits: 1, maximumFractionDigits: 1 })
const twoDecimal = new Intl.NumberFormat('en-GB', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const dayTime = new Intl.DateTimeFormat('en-GB', { weekday: 'short', hour: '2-digit', minute: '2-digit', hour12: false })
const clock = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hour12: false })
const relative = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })

/** Single source of truth for currency, emission, and time formatting in the UI. */
export const formatDkk = (value: number) => currency.format(value)
export const formatPricePerKwh = (value: number) => `${twoDecimal.format(value)} kr/kWh`
export const formatKwh = (value: number) => `${oneDecimal.format(value)} kWh`

/** Emissions arrive in grams; kilograms read more naturally once a session passes 1 kg. */
export const formatCo2 = (grams: number) =>
  Math.abs(grams) >= 1000 ? `${oneDecimal.format(grams / 1000)} kg CO₂` : `${Math.round(grams)} g CO₂`

export const formatDayTime = (value: Date | string | number) => dayTime.format(new Date(value))
export const formatClock = (value: Date | string | number) => clock.format(new Date(value))

export const formatRelative = (value: Date | string | number, now = Date.now()) => {
  const diffMinutes = Math.round((new Date(value).getTime() - now) / 60_000)
  if (Math.abs(diffMinutes) < 60) return relative.format(diffMinutes, 'minute')
  const diffHours = Math.round(diffMinutes / 60)
  if (Math.abs(diffHours) < 24) return relative.format(diffHours, 'hour')
  return relative.format(Math.round(diffHours / 24), 'day')
}

export const formatDuration = (hours: number) => {
  if (!Number.isFinite(hours) || hours <= 0) return '0 min'
  const whole = Math.floor(hours)
  const minutes = Math.round((hours - whole) * 60)
  if (!whole) return `${minutes} min`
  return minutes ? `${whole} h ${minutes} min` : `${whole} h`
}

export const strategyInfo: Record<OptimizationAlgorithm, { label: string; technical: string; description: string }> = {
  greedy: { label: 'Balanced and fast', technical: 'Greedy heuristic', description: 'A quick, dependable plan that balances price and emissions.' },
  optimal: { label: 'Best overall balance', technical: 'Dynamic programming', description: 'Explores more combinations to find the strongest weighted result.' },
  mip: { label: 'Most precise', technical: 'Mixed-integer programming', description: 'Uses a mathematical model for a highly constrained plan.' },
  naive: { label: 'Charge immediately', technical: 'Naive baseline', description: 'Starts charging now and provides a useful comparison baseline.' },
}
