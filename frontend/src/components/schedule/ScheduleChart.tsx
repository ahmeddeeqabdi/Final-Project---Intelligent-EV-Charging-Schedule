import { useEffect, useMemo, useState } from 'react'
import {
  Area,
  Bar,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  type DefaultLegendContentProps,
} from 'recharts'
import { Spinner } from '@/components/ui/spinner'
import { type MarketSignalPoint, type ScheduledSlot } from '@/types/api'

interface ScheduleChartProps {
  slots: ScheduledSlot[]
  marketSignals: MarketSignalPoint[]
  isLoading: boolean
  windowStart?: string | null
  windowEnd?: string | null

}

interface ChartPoint {
  timestamp: string
  timestampMs: number
  intervalLabel: string
  powerValue: number
  energyPrice: number | null
  co2Intensity: number | null
  energyPriceDisplay: number | null
  co2IntensityDisplay: number | null
  actionLabel: string
  efficiencyPct: number
}

interface TooltipContentProps {
  active?: boolean
  payload?: Array<{ payload?: ChartPoint }>
}

interface MarkerBadgeProps {
  viewBox?: { x?: number; y?: number }
  text: string
  color: string
}

const POWER_COLOR = 'hsl(var(--primary))'
const PRICE_COLOR = 'hsl(var(--foreground))'
const CO2_COLOR = 'hsl(var(--muted-foreground))'
const RIGHT_AXIS_COLOR = 'hsl(var(--foreground))'
const GRID_COLOR = 'hsl(var(--border))'

const twoDecimal = new Intl.NumberFormat('en-DK', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const integerFormatter = new Intl.NumberFormat('en-DK', {
  maximumFractionDigits: 0,
})

const toLabel = (timestamp: string): string => {
  const date = new Date(timestamp)
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${hours}:${minutes}`
}

const normalizeToBand = (value: number, min: number, max: number): number => {
  if (max <= min) {
    return 50
  }

  const ratio = (value - min) / (max - min)
  return 15 + ratio * 70
}

const toIntervalLabel = (timestamp: string): string => {
  const start = new Date(timestamp)
  const end = new Date(start)
  end.setHours(end.getHours() + 1)
  return `${toLabel(start.toISOString())} - ${toLabel(end.toISOString())}`
}

const getInterval = (width: number, points: number): number => {
  if (points <= 8) {
    return 0
  }

  if (width < 640) {
    return Math.max(0, Math.ceil(points / 5) - 1)
  }

  if (width < 960) {
    return Math.max(0, Math.ceil(points / 7) - 1)
  }

  return Math.max(0, Math.ceil(points / 10) - 1)
}

const MarkerBadge = ({ viewBox, text, color }: MarkerBadgeProps) => {
  if (!viewBox) {
    return null
  }

  const x = viewBox.x ?? 0
  const y = (viewBox.y ?? 0) - 24

  return (
    <g>
      <text x={x} y={y + 12} fill={color} fontSize={10} fontFamily='"IBM Plex Sans", sans-serif' fontWeight={600} letterSpacing="0.1em" textAnchor="middle">
        {text}
      </text>
    </g>
  )
}

export function ScheduleChart({
  slots,
  marketSignals,
  isLoading,
  windowStart = null,
  windowEnd = null,
}: ScheduleChartProps) {
  const legendPayload: NonNullable<DefaultLegendContentProps['payload']> = [
    { value: 'Price', type: 'square', color: PRICE_COLOR },
    { value: 'CO2 Score', type: 'square', color: CO2_COLOR },
    { value: 'Power', type: 'square', color: POWER_COLOR },
  ]
  const [viewportWidth, setViewportWidth] = useState<number>(() => {
    if (typeof window === 'undefined') {
      return 1280
    }
    return window.innerWidth
  })

  useEffect(() => {
    const onResize = () => {
      setViewportWidth(window.innerWidth)
    }

    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  const chartData = useMemo<ChartPoint[]>(() => {
    if (!slots.length && !marketSignals.length) {
      return []
    }

    const enrichPoints = (
      basePoints: Array<Pick<ChartPoint, 'timestamp' | 'timestampMs' | 'powerValue' | 'energyPrice' | 'co2Intensity'>>,
    ): ChartPoint[] => {
      const peakPower = Math.max(0, ...basePoints.map((point) => point.powerValue))
      const prices = basePoints.map((point) => point.energyPrice).filter((value): value is number => value != null)
      const co2Values = basePoints.map((point) => point.co2Intensity).filter((value): value is number => value != null)
      const minPrice = prices.length ? Math.min(...prices) : 0
      const maxPrice = prices.length ? Math.max(...prices) : 0
      const minCo2 = co2Values.length ? Math.min(...co2Values) : 0
      const maxCo2 = co2Values.length ? Math.max(...co2Values) : 0

      return basePoints.map((point) => {
        const efficiencyPct = peakPower > 0 ? (point.powerValue / peakPower) * 100 : 0

        return {
          ...point,
          intervalLabel: toIntervalLabel(point.timestamp),
          energyPriceDisplay:
            point.energyPrice == null ? null : normalizeToBand(point.energyPrice, minPrice, maxPrice),
          co2IntensityDisplay:
            point.co2Intensity == null ? null : normalizeToBand(point.co2Intensity, minCo2, maxCo2),
          actionLabel:
            point.powerValue > 0
              ? `Charging (${twoDecimal.format(point.powerValue)} kW)`
              : 'Idle (no charging)',
          efficiencyPct,
        }
      })
    }

    const slotByHour = new Map<string, { power: number }>()
    for (const slot of slots) {
      const key = new Date(slot.timestamp).toISOString().slice(0, 13)
      const current = slotByHour.get(key)
      if (current) {
        current.power += slot.powerValue
      } else {
        slotByHour.set(key, { power: slot.powerValue })
      }
    }

    const marketByHour = new Map<string, { price: number | null; co2: number | null }>()
    for (const point of marketSignals) {
      const key = new Date(point.timestamp).toISOString().slice(0, 13)
      marketByHour.set(key, {
        price: point.energyPrice,
        co2: point.co2Intensity,
      })
    }

    const sortedSlotTimes = slots
      .map((slot) => new Date(slot.timestamp).getTime())
      .filter((value) => !Number.isNaN(value))
      .sort((a, b) => a - b)
    const sortedSignalTimes = marketSignals
      .map((point) => new Date(point.timestamp).getTime())
      .filter((value) => !Number.isNaN(value))
      .sort((a, b) => a - b)

    const earliestTime = sortedSlotTimes[0] ?? sortedSignalTimes[0]
    const latestTime = sortedSignalTimes[sortedSignalTimes.length - 1] ?? sortedSlotTimes[sortedSlotTimes.length - 1]

    if (earliestTime == null || latestTime == null) {
      return []
    }

    const inferredStart = new Date(earliestTime)
    const inferredEnd = new Date(latestTime)

    const explicitStart = windowStart ? new Date(windowStart) : null
    const explicitEnd = windowEnd ? new Date(windowEnd) : null

    const start = explicitStart && !Number.isNaN(explicitStart.getTime()) ? explicitStart : inferredStart
    const end = explicitEnd && !Number.isNaN(explicitEnd.getTime()) ? explicitEnd : inferredEnd

    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || end < start) {
      return []
    }

    const cursor = new Date(start)
    cursor.setMinutes(0, 0, 0)
    const points: Array<Pick<ChartPoint, 'timestamp' | 'timestampMs' | 'powerValue' | 'energyPrice' | 'co2Intensity'>> = []

    while (cursor <= end) {
      const timestamp = cursor.toISOString()
      const key = timestamp.slice(0, 13)
      const slot = slotByHour.get(key)
      const market = marketByHour.get(key)

      points.push({
        timestamp,
        timestampMs: cursor.getTime(),
        powerValue: slot?.power ?? 0,
        energyPrice: market?.price ?? null,
        co2Intensity: market?.co2 ?? null,
      })

      cursor.setHours(cursor.getHours() + 1)
    }

    return enrichPoints(points)
  }, [slots, marketSignals, windowStart, windowEnd])

  const departureMarkerMs = useMemo(() => {
    if (!windowEnd || !chartData.length) {
      return null
    }

    const departureMs = new Date(windowEnd).getTime()
    if (Number.isNaN(departureMs)) {
      return null
    }

    const chartMinMs = chartData[0].timestampMs
    const chartMaxMs = chartData[chartData.length - 1].timestampMs

    return Math.max(chartMinMs, Math.min(departureMs, chartMaxMs))
  }, [chartData, windowEnd])

  const stepMs = useMemo(() => {
    if (chartData.length < 2) {
      return 60 * 60 * 1000
    }

    for (let index = 1; index < chartData.length; index += 1) {
      const diff = chartData[index].timestampMs - chartData[index - 1].timestampMs
      if (diff > 0) {
        return diff
      }
    }

    return 60 * 60 * 1000
  }, [chartData])

  const xDomain = useMemo<[number, number]>(() => {
    if (!chartData.length) {
      return [0, 1]
    }

    const first = chartData[0].timestampMs
    const last = chartData[chartData.length - 1].timestampMs
    const halfStep = stepMs / 2
    return [first - halfStep, last + halfStep]
  }, [chartData, stepMs])

  const tickInterval = useMemo(() => getInterval(viewportWidth, chartData.length), [viewportWidth, chartData.length])

  const xTicks = useMemo<number[]>(() => {
    if (!chartData.length) {
      return []
    }

    const tickEvery = Math.max(1, tickInterval + 1)
    const ticks = chartData
      .filter((_point: ChartPoint, index: number) => index % tickEvery === 0)
      .map((point: ChartPoint) => point.timestampMs)

    const lastTick = chartData[chartData.length - 1].timestampMs
    if (ticks[ticks.length - 1] !== lastTick) {
      ticks.push(lastTick)
    }

    return ticks
  }, [chartData, tickInterval])

  if (isLoading) {
    return (
      <div className="pt-2 lg:pt-1">
        <h3 className="mb-3 font-display text-xl font-semibold text-foreground">Charging signals</h3>
        <div className="flex min-h-[260px] items-center justify-center border border-border bg-card">
          <div className="inline-flex min-h-11 items-center gap-2 text-base font-semibold text-muted-foreground">
            <Spinner className="h-5 w-5" />
            Rendering optimized schedule...
          </div>
        </div>
      </div>
    )
  }

  if (!chartData.length) {
    return (
      <div className="pt-2 lg:pt-1">
        <h3 className="mb-3 font-display text-xl font-semibold text-foreground">Charging signals</h3>
        <div className="flex min-h-[260px] items-center justify-center border border-border bg-card p-6 text-center text-base font-semibold text-muted-foreground">
          Submit constraints to visualize charging power, spot prices, and CO2 intensity over time.
        </div>
      </div>
    )
  }

  return (
    <div className="pt-2 lg:pt-1">
      <h3 className="mb-3 font-display text-xl font-semibold text-foreground">Charging signals</h3>
      <div className="px-0 pb-4 lg:pr-2">
        <div className="h-[240px] w-full sm:h-[300px]">
          <ResponsiveContainer width="100%" height="100%">
            <ComposedChart data={chartData} margin={{ top: 10, right: 35, left: 0, bottom: 4 }}>
              {departureMarkerMs ? (
                <ReferenceLine
                  x={departureMarkerMs}
                  stroke={RIGHT_AXIS_COLOR}
                  strokeWidth={2}
                  strokeDasharray="4 4"
                  yAxisId="left"
                  ifOverflow="extendDomain"
                  label={<MarkerBadge text="DEPARTURE" color={RIGHT_AXIS_COLOR} />}
                />
              ) : null}
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke={GRID_COLOR} />
              <XAxis
                type="number"
                dataKey="timestampMs"
                domain={xDomain}
                ticks={xTicks}
                tick={{ fontSize: 11, fontFamily: '"IBM Plex Sans", sans-serif', fill: '#5C5C5C' }}
                tickFormatter={(value: number) => toLabel(new Date(value).toISOString())}
                minTickGap={14}
                axisLine={{ stroke: GRID_COLOR }}
                tickLine={{ stroke: GRID_COLOR }}
              />
              <YAxis
                yAxisId="left"
                tick={{ fontSize: 11, fontFamily: '"IBM Plex Sans", sans-serif', fill: POWER_COLOR }}
                tickFormatter={(value: number) => `${integerFormatter.format(value)}`}
                label={{ value: 'Power (kW)', angle: -90, position: 'insideLeft', fill: POWER_COLOR, fontSize: 11, fontFamily: '"IBM Plex Sans", sans-serif', fontWeight: 600 }}
                width={50}
                axisLine={{ stroke: GRID_COLOR }}
                tickLine={{ stroke: GRID_COLOR }}
              />
              <YAxis
                yAxisId="right"
                orientation="right"
                tick={{ fontSize: 11, fontFamily: '"IBM Plex Sans", sans-serif', fill: RIGHT_AXIS_COLOR }}
                tickFormatter={(value: number) => `${integerFormatter.format(value)}%`}
                domain={[0, 100]}
                label={{
                  value: 'Relative score',
                  angle: 90,
                  position: 'center',
                  dx: 30,
                  fill: RIGHT_AXIS_COLOR,
                  fontSize: 10,
                  fontFamily: '"IBM Plex Sans", sans-serif',
                  fontWeight: 600,
                  letterSpacing: '0'
                }}
                width={70}
                axisLine={{ stroke: GRID_COLOR }}
                tickLine={false}
              />
              <Tooltip
                cursor={{ fill: 'rgba(224, 221, 213, 0.4)' }}
                content={({ active, payload }: TooltipContentProps) => {
                  if (!active || !payload?.length) {
                    return null
                  }

                  const point = payload[0]?.payload as ChartPoint | undefined
                  if (!point) {
                    return null
                  }

                  return (
                    <div className="min-w-[220px] bg-card border border-border p-3 shadow-hard-sm text-base">
                      <p className="mb-3 text-base font-semibold text-muted-foreground">{point.intervalLabel}</p>
                      <div className="space-y-1.5 tabular-nums">
                        <p className="flex justify-between">
                          <span className="font-semibold text-foreground mr-6">Action</span> {point.actionLabel}
                        </p>
                        <p className="flex justify-between">
                          <span className="font-semibold text-foreground mr-6">Price</span>{' '}
                          {point.energyPrice == null ? 'N/A' : `${twoDecimal.format(point.energyPrice)} kr/kWh`}
                        </p>
                        <p className="flex justify-between">
                          <span className="font-semibold text-foreground mr-6">CO2</span>{' '}
                          {point.co2Intensity == null ? 'N/A' : `${twoDecimal.format(point.co2Intensity)} g/kWh`}
                        </p>
                      </div>
                    </div>
                  )
                }}
              />
              <Legend
                iconType="square"
                payload={legendPayload}
                wrapperStyle={{ paddingTop: '20px', fontSize: '12px', fontFamily: '"IBM Plex Sans", sans-serif' }}
              />
              <Area
                yAxisId="right"
                type="step"
                dataKey="energyPriceDisplay"
                name="Price"
                stroke="none"
                fill={PRICE_COLOR}
                fillOpacity={0.08}
                legendType="none"
                connectNulls
              />
              <Area
                yAxisId="right"
                type="step"
                dataKey="co2IntensityDisplay"
                name="CO2 Score"
                stroke="none"
                fill={CO2_COLOR}
                fillOpacity={0.08}
                legendType="none"
                connectNulls
              />
              <Bar yAxisId="left" dataKey="powerValue" name="Power" fill={POWER_COLOR} radius={[2, 2, 0, 0]} maxBarSize={48} />
              <Line
                yAxisId="right"
                type="step"
                dataKey="energyPriceDisplay"
                name="Price"
                stroke={PRICE_COLOR}
                strokeWidth={2}
                dot={{ r: 2, fill: PRICE_COLOR }}
                connectNulls
              />
              <Line
                yAxisId="right"
                type="step"
                dataKey="co2IntensityDisplay"
                name="CO2"
                stroke={CO2_COLOR}
                strokeWidth={2}
                dot={{ r: 2, fill: CO2_COLOR }}
                connectNulls
              />
            </ComposedChart>
          </ResponsiveContainer>
        </div>
        <p className="mt-6 text-base text-muted-foreground text-center tracking-wide font-sans">
          Display normalized for comparison. Tooltip values remain raw units.
        </p>
      </div>
    </div>
  )
}
