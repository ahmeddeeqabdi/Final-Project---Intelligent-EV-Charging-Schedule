import { useMemo, useState } from 'react'
import { Zap } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatClock, formatDayTime, formatPricePerKwh } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { ScheduleResult } from '@/types/api'

interface ChargingTimelineProps {
  result: ScheduleResult
  windowStart?: string | null
  windowEnd?: string | null
}

interface HourCell {
  timestampMs: number
  price: number | null
  co2: number | null
  powerKw: number
}

const HOUR_MS = 3_600_000
const hourKey = (ms: number) => Math.floor(ms / HOUR_MS)

const describe = (cell: HourCell) => [
  `${formatClock(cell.timestampMs)}–${formatClock(cell.timestampMs + HOUR_MS)}`,
  cell.powerKw > 0.01 ? `charging ${cell.powerKw.toFixed(1)} kW` : 'idle',
  cell.price == null ? null : formatPricePerKwh(cell.price),
  cell.co2 == null ? null : `${Math.round(cell.co2)} g CO₂/kWh`,
].filter(Boolean).join(' · ')

/**
 * Hour-by-hour strip of the charging window: bar height is the electricity price,
 * highlighted bars are the hours the plan charges. It answers "when will my car
 * charge, and why then?" without opening the detailed chart.
 */
export function ChargingTimeline({ result, windowStart, windowEnd }: ChargingTimelineProps) {
  const cells = useMemo<HourCell[]>(() => {
    const market = new Map<number, { price: number | null; co2: number | null }>()
    for (const point of result.marketSignals) {
      const ms = new Date(point.timestamp).getTime()
      if (!Number.isNaN(ms)) market.set(hourKey(ms), { price: point.energyPrice, co2: point.co2Intensity })
    }
    const power = new Map<number, number>()
    for (const slot of result.slots) {
      const ms = new Date(slot.timestamp).getTime()
      if (Number.isNaN(ms)) continue
      const key = hourKey(ms)
      power.set(key, (power.get(key) ?? 0) + Math.max(0, slot.powerValue))
      if (!market.has(key)) market.set(key, { price: slot.energyPrice, co2: slot.co2Intensity })
    }

    const keys = [...market.keys(), ...power.keys()]
    if (!keys.length) return []
    const startMs = windowStart ? new Date(windowStart).getTime() : NaN
    const endMs = windowEnd ? new Date(windowEnd).getTime() : NaN
    const first = Number.isNaN(startMs) ? Math.min(...keys) : hourKey(startMs)
    // The departure hour itself is not chargeable, so stop at the hour before it.
    const last = Number.isNaN(endMs) ? Math.max(...keys) : Math.ceil(endMs / HOUR_MS) - 1
    const out: HourCell[] = []
    for (let key = first; key <= last && out.length < 96; key += 1) {
      const signal = market.get(key)
      out.push({ timestampMs: key * HOUR_MS, price: signal?.price ?? null, co2: signal?.co2 ?? null, powerKw: power.get(key) ?? 0 })
    }
    return out
  }, [result, windowStart, windowEnd])

  const [activeIndex, setActiveIndex] = useState<number | null>(null)
  if (!cells.length) return null

  const prices = cells.map((cell) => cell.price).filter((value): value is number => value != null)
  const minPrice = prices.length ? Math.min(...prices) : 0
  const maxPrice = prices.length ? Math.max(...prices) : 1
  const charging = cells.filter((cell) => cell.powerKw > 0.01)
  const energy = charging.reduce((sum, cell) => sum + cell.powerKw, 0)
  const paidAverage = energy > 0 ? charging.reduce((sum, cell) => sum + cell.powerKw * (cell.price ?? 0), 0) / energy : null
  const windowAverage = prices.length ? prices.reduce((sum, value) => sum + value, 0) / prices.length : null
  const labelEvery = cells.length <= 8 ? 1 : cells.length <= 16 ? 2 : cells.length <= 30 ? 4 : 6
  const summary = `${charging.length} charging ${charging.length === 1 ? 'hour' : 'hours'} between ${formatDayTime(cells[0].timestampMs)} and ${formatDayTime(cells[cells.length - 1].timestampMs + HOUR_MS)}.`

  return (
    <Card className="animate-rise">
      <CardHeader className="pb-3">
        <div className="flex flex-wrap items-end justify-between gap-x-6 gap-y-2">
          <div className="space-y-1.5">
            <CardTitle className="text-lg">When your car charges</CardTitle>
            <CardDescription>Taller bars mean pricier electricity. Your plan uses the highlighted hours.</CardDescription>
          </div>
          {paidAverage != null && windowAverage != null ? (
            <dl className="flex gap-5 text-sm">
              <div>
                <dt className="text-xs text-muted-foreground">You pay on average</dt>
                <dd className="font-display text-base font-bold text-primary">{formatPricePerKwh(paidAverage)}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Window average</dt>
                <dd className="font-display text-base font-bold">{formatPricePerKwh(windowAverage)}</dd>
              </div>
            </dl>
          ) : null}
        </div>
      </CardHeader>
      <CardContent>
        <div role="img" aria-label={summary}>
          <div className="flex h-36 items-end gap-[3px] border-b border-border sm:h-40" aria-hidden="true">
            {cells.map((cell, index) => {
              const isCharging = cell.powerKw > 0.01
              const ratio = cell.price == null || maxPrice <= minPrice ? 0.5 : (cell.price - minPrice) / (maxPrice - minPrice)
              return (
                <div
                  key={cell.timestampMs}
                  className={cn('group relative flex h-full min-w-0 flex-1 cursor-pointer flex-col justify-end', activeIndex === index && '[&>div]:brightness-90')}
                  onPointerEnter={() => setActiveIndex(index)}
                  onClick={() => setActiveIndex(index)}
                >
                  {isCharging ? <Zap className="mx-auto mb-1 h-3 w-3 shrink-0 text-primary" fill="currentColor" /> : null}
                  <div
                    className={cn(
                      'w-full rounded-t-[3px] transition-[filter] group-hover:brightness-90',
                      isCharging ? 'bg-primary' : 'bg-muted-foreground/25 dark:bg-muted-foreground/35',
                    )}
                    style={{ height: `${18 + ratio * 70}%` }}
                  />
                </div>
              )
            })}
          </div>
          <div className="mt-2 flex gap-[3px] text-[11px] tabular-nums text-muted-foreground" aria-hidden="true">
            {cells.map((cell, index) => (
              <div key={cell.timestampMs} className="relative min-w-0 flex-1">
                {index % labelEvery === 0 ? <span className="absolute left-0 whitespace-nowrap">{formatClock(cell.timestampMs)}</span> : null}
              </div>
            ))}
          </div>
        </div>
        <div className="mt-7 flex flex-wrap items-center gap-x-5 gap-y-2 text-xs text-muted-foreground">
          <span className="inline-flex items-center gap-1.5"><span className="h-3 w-3 rounded-sm bg-primary" />Charging</span>
          <span className="inline-flex items-center gap-1.5"><span className="h-3 w-3 rounded-sm bg-muted-foreground/25 dark:bg-muted-foreground/35" />Idle</span>
          <span className="ml-auto tabular-nums" aria-live="polite">
            {activeIndex == null ? 'Hover or tap a bar for details' : describe(cells[activeIndex])}
          </span>
        </div>
      </CardContent>
    </Card>
  )
}
