import { useMemo } from 'react'
import { Area, Bar, CartesianGrid, ComposedChart, Legend, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { formatClock, formatPricePerKwh } from '@/lib/format'
import { type MarketSignalPoint, type ScheduledSlot } from '@/types/api'

interface ScheduleChartProps {
  slots: ScheduledSlot[]
  marketSignals: MarketSignalPoint[]
  windowStart?: string | null
  windowEnd?: string | null
}

interface ChartPoint {
  timestampMs: number
  powerValue: number
  energyPrice: number | null
  co2Intensity: number | null
  priceScore: number | null
  co2Score: number | null
}

const HOUR_MS = 3_600_000
const POWER_COLOR = 'hsl(var(--primary))'
const PRICE_COLOR = 'hsl(var(--foreground))'
const CO2_COLOR = 'hsl(var(--muted-foreground))'
const GRID_COLOR = 'hsl(var(--border))'
const TICK = { fontSize: 11, fill: 'hsl(var(--muted-foreground))' }

const hourKey = (timestamp: string) => Math.floor(new Date(timestamp).getTime() / HOUR_MS)

/** Maps a series into a 15–85% band so price and CO₂ can share one relative axis. */
const toBand = (values: Array<number | null>) => {
  const known = values.filter((value): value is number => value != null)
  const min = Math.min(...known)
  const max = Math.max(...known)
  return values.map((value) => (value == null ? null : max > min ? 15 + ((value - min) / (max - min)) * 70 : 50))
}

export function ScheduleChart({ slots, marketSignals, windowStart = null, windowEnd = null }: ScheduleChartProps) {
  const data = useMemo<ChartPoint[]>(() => {
    const power = new Map<number, number>()
    for (const slot of slots) power.set(hourKey(slot.timestamp), (power.get(hourKey(slot.timestamp)) ?? 0) + slot.powerValue)
    const market = new Map(marketSignals.map((point) => [hourKey(point.timestamp), point]))

    const keys = [...power.keys(), ...market.keys()].filter(Number.isFinite)
    if (!keys.length) return []
    const explicitStart = windowStart ? hourKey(windowStart) : NaN
    const explicitEnd = windowEnd ? hourKey(windowEnd) : NaN
    const first = Number.isFinite(explicitStart) ? explicitStart : Math.min(...keys)
    const last = Number.isFinite(explicitEnd) ? explicitEnd : Math.max(...keys)

    const base = Array.from({ length: Math.max(0, last - first + 1) }, (_, index) => {
      const key = first + index
      return {
        timestampMs: key * HOUR_MS,
        powerValue: power.get(key) ?? 0,
        energyPrice: market.get(key)?.energyPrice ?? null,
        co2Intensity: market.get(key)?.co2Intensity ?? null,
      }
    })
    const priceScores = toBand(base.map((point) => point.energyPrice))
    const co2Scores = toBand(base.map((point) => point.co2Intensity))
    return base.map((point, index) => ({ ...point, priceScore: priceScores[index], co2Score: co2Scores[index] }))
  }, [slots, marketSignals, windowStart, windowEnd])

  if (!data.length) {
    return <p className="text-sm text-muted-foreground">No market data is available for this charging window.</p>
  }

  const departureMs = windowEnd ? new Date(windowEnd).getTime() : NaN
  const firstMs = data[0].timestampMs
  const lastMs = data[data.length - 1].timestampMs

  return (
    <div>
      <div className="h-[260px] w-full sm:h-[320px]">
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart data={data} margin={{ top: 20, right: 8, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke={GRID_COLOR} />
            <XAxis
              type="number"
              dataKey="timestampMs"
              domain={[firstMs - HOUR_MS / 2, lastMs + HOUR_MS / 2]}
              ticks={data.map((point) => point.timestampMs)}
              interval="preserveStartEnd"
              minTickGap={24}
              tick={TICK}
              tickFormatter={formatClock}
              axisLine={{ stroke: GRID_COLOR }}
              tickLine={false}
            />
            <YAxis yAxisId="power" tick={TICK} width={44} axisLine={false} tickLine={false} label={{ value: 'kW', position: 'insideTopLeft', dy: -18, fill: POWER_COLOR, fontSize: 11, fontWeight: 600 }} />
            <YAxis yAxisId="score" orientation="right" domain={[0, 100]} hide />
            {Number.isFinite(departureMs) ? (
              <ReferenceLine
                yAxisId="power"
                x={Math.min(Math.max(departureMs, firstMs), lastMs)}
                stroke={PRICE_COLOR}
                strokeDasharray="4 4"
                label={{ value: 'Departure', position: 'top', fill: PRICE_COLOR, fontSize: 11, fontWeight: 600 }}
              />
            ) : null}
            <Tooltip
              cursor={{ fill: 'hsl(var(--muted) / 0.5)' }}
              content={({ active, payload }) => {
                const point = payload?.[0]?.payload as ChartPoint | undefined
                if (!active || !point) return null
                const rows: Array<[string, string]> = [
                  ['Charging', point.powerValue > 0 ? `${point.powerValue.toFixed(1)} kW` : 'Idle'],
                  ['Price', point.energyPrice == null ? 'N/A' : formatPricePerKwh(point.energyPrice)],
                  ['CO₂', point.co2Intensity == null ? 'N/A' : `${Math.round(point.co2Intensity)} g/kWh`],
                ]
                return (
                  <div className="min-w-[200px] rounded-md border border-border bg-card p-3 text-sm shadow-hard-sm">
                    <p className="mb-2 font-semibold text-muted-foreground">{formatClock(point.timestampMs)}–{formatClock(point.timestampMs + HOUR_MS)}</p>
                    {rows.map(([label, value]) => (
                      <p key={label} className="flex justify-between gap-6 tabular-nums"><span className="font-semibold">{label}</span>{value}</p>
                    ))}
                  </div>
                )
              }}
            />
            <Legend iconType="square" wrapperStyle={{ paddingTop: 12, fontSize: 12 }} />
            <Area yAxisId="score" type="step" dataKey="priceScore" name="Price (relative)" stroke={PRICE_COLOR} strokeWidth={2} fill={PRICE_COLOR} fillOpacity={0.05} activeDot={false} connectNulls />
            <Area yAxisId="score" type="step" dataKey="co2Score" name="CO₂ (relative)" stroke={CO2_COLOR} strokeWidth={2} fill={CO2_COLOR} fillOpacity={0.05} activeDot={false} connectNulls />
            <Bar yAxisId="power" dataKey="powerValue" name="Charging power" fill={POWER_COLOR} radius={[2, 2, 0, 0]} maxBarSize={48} />
          </ComposedChart>
        </ResponsiveContainer>
      </div>
      <p className="mt-3 text-center text-xs text-muted-foreground">
        Price and CO₂ are scaled to the same relative range so they can share an axis. Hover for real values.
      </p>
    </div>
  )
}
