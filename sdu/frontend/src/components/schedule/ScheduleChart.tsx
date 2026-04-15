import { useEffect, useMemo, useState } from 'react'
import {
  Area,
  Bar,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ReferenceArea,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
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

const POWER_COLOR = '#2DD4BF'
const PRICE_COLOR = '#FACC15'
const CO2_COLOR = '#A855F7'
const RIGHT_AXIS_COLOR = '#94A3B8'

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
  const width = Math.max(56, text.length * 7 + 14)

  return (
    <g>
      <rect
        x={x - width / 2}
        y={y - 14}
        width={width}
        height={18}
        rx={6}
        ry={6}
        fill={color}
        fillOpacity={0.2}
        stroke={color}
        strokeOpacity={0.75}
      />
      <text x={x} y={y - 1} fill={color} fontSize={11} fontWeight={700} textAnchor="middle">
        {text}
      </text>
    </g>
  )
}

export function ScheduleChart({ slots, marketSignals, isLoading, windowStart = null, windowEnd = null }: ScheduleChartProps) {
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

  const constraintWindowBounds = useMemo(() => {
    if (!windowStart || !windowEnd || !chartData.length) {
      return null
    }

    const startDate = new Date(windowStart)
    const endDate = new Date(windowEnd)

    if (Number.isNaN(startDate.getTime()) || Number.isNaN(endDate.getTime()) || endDate <= startDate) {
      return null
    }

    const chartMinMs = chartData[0].timestampMs
    const chartMaxMs = chartData[chartData.length - 1].timestampMs
    const x1 = Math.max(chartMinMs, Math.min(startDate.getTime(), chartMaxMs))
    const x2 = Math.max(chartMinMs, Math.min(endDate.getTime(), chartMaxMs))

    if (x1 > x2) {
      return null
    }

    return { x1, x2 }
  }, [chartData, windowStart, windowEnd])

  const windowMarkers = useMemo(() => {
    if (!windowStart || !windowEnd || !chartData.length) {
      return null
    }

    const plugInMs = new Date(windowStart).getTime()
    const departureMs = new Date(windowEnd).getTime()
    if (Number.isNaN(plugInMs) || Number.isNaN(departureMs) || departureMs <= plugInMs) {
      return null
    }

    const chartMinMs = chartData[0].timestampMs
    const chartMaxMs = chartData[chartData.length - 1].timestampMs

    return {
      plugInMs: Math.max(chartMinMs, Math.min(plugInMs, chartMaxMs)),
      departureMs: Math.max(chartMinMs, Math.min(departureMs, chartMaxMs)),
    }
  }, [chartData, windowStart, windowEnd])

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
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Charging Plan Timeline</CardTitle>
        </CardHeader>
        <CardContent className="flex min-h-[280px] items-center justify-center">
          <div className="inline-flex min-h-11 items-center gap-2 text-sm text-muted-foreground">
            <Spinner className="h-5 w-5" />
            Rendering optimized schedule...
          </div>
        </CardContent>
      </Card>
    )
  }

  if (!chartData.length) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Charging Plan Timeline</CardTitle>
        </CardHeader>
        <CardContent className="flex min-h-[220px] items-center justify-center text-center text-sm text-muted-foreground">
          Submit constraints to visualize charging power, spot prices, and CO2 intensity over time.
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">Charging Plan Timeline</CardTitle>
      </CardHeader>
      <CardContent className="px-2 pb-4 sm:px-4">
        <div className="h-[340px] w-full sm:h-[380px]">
          <ResponsiveContainer width="100%" height="100%">
            <ComposedChart data={chartData} margin={{ top: 10, right: 15, left: 0, bottom: 4 }}>
              {constraintWindowBounds ? (
                <ReferenceArea
                  x1={constraintWindowBounds.x1}
                  x2={constraintWindowBounds.x2}
                  yAxisId="left"
                  ifOverflow="extendDomain"
                  fill={RIGHT_AXIS_COLOR}
                  fillOpacity={0.08}
                />
              ) : null}
              {windowMarkers ? (
                <ReferenceLine
                  x={windowMarkers.plugInMs}
                  stroke={POWER_COLOR}
                  strokeWidth={2.5}
                  strokeDasharray="5 4"
                  yAxisId="left"
                  ifOverflow="extendDomain"
                  label={<MarkerBadge text="Plug-in" color={POWER_COLOR} />}
                />
              ) : null}
              {windowMarkers ? (
                <ReferenceLine
                  x={windowMarkers.departureMs}
                  stroke="#EF4444"
                  strokeWidth={2.5}
                  strokeDasharray="5 4"
                  yAxisId="left"
                  ifOverflow="extendDomain"
                  label={<MarkerBadge text="Deadline" color="#EF4444" />}
                />
              ) : null}
              <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
              <XAxis
                type="number"
                dataKey="timestampMs"
                domain={xDomain}
                ticks={xTicks}
                tick={{ fontSize: 12 }}
                tickFormatter={(value: number) => toLabel(new Date(value).toISOString())}
                minTickGap={14}
              />
              <YAxis
                yAxisId="left"
                tick={{ fontSize: 12, fill: POWER_COLOR }}
                tickFormatter={(value: number) => `${integerFormatter.format(value)} kW`}
                label={{ value: 'Power (kW)', angle: -90, position: 'insideLeft', fill: POWER_COLOR, fontSize: 12 }}
                width={58}
              />
              <YAxis
                yAxisId="right"
                orientation="right"
                tick={{ fontSize: 12, fill: RIGHT_AXIS_COLOR }}
                tickFormatter={(value: number) => `${integerFormatter.format(value)}%`}
                domain={[0, 100]}
                label={{
                  value: 'Relative Objective Score',
                  angle: 90,
                  position: 'insideRight',
                  fill: RIGHT_AXIS_COLOR,
                  fontSize: 12,
                }}
                width={86}
              />
              <Tooltip
                content={({ active, payload }: TooltipContentProps) => {
                  if (!active || !payload?.length) {
                    return null
                  }

                  const point = payload[0]?.payload as ChartPoint | undefined
                  if (!point) {
                    return null
                  }

                  return (
                    <div className="min-w-[220px] rounded-md border border-border/60 bg-card/95 p-3 text-sm shadow-lg backdrop-blur-sm">
                      <p className="text-xs font-semibold text-muted-foreground">{point.intervalLabel}</p>
                      <div className="mt-2 space-y-1.5">
                        <p>
                          <span className="font-medium text-muted-foreground">Action:</span> {point.actionLabel}
                        </p>
                        <p>
                          <span className="font-medium text-muted-foreground">Price:</span>{' '}
                          {point.energyPrice == null ? 'N/A' : `${twoDecimal.format(point.energyPrice)} ore/kWh`}
                        </p>
                        <p>
                          <span className="font-medium text-muted-foreground">CO2:</span>{' '}
                          {point.co2Intensity == null ? 'N/A' : `${twoDecimal.format(point.co2Intensity)} g/kWh`}
                        </p>
                        <p>
                          <span className="font-medium text-muted-foreground">Efficiency:</span>{' '}
                          {`${twoDecimal.format(point.efficiencyPct)}%`}
                        </p>
                      </div>
                    </div>
                  )
                }}
              />
              <Legend />
              <Area
                yAxisId="right"
                type="monotone"
                dataKey="energyPriceDisplay"
                name="Price Landscape"
                stroke="none"
                fill={PRICE_COLOR}
                fillOpacity={0.16}
                legendType="none"
                connectNulls
              />
              <Area
                yAxisId="right"
                type="monotone"
                dataKey="co2IntensityDisplay"
                name="CO2 Landscape"
                stroke="none"
                fill={CO2_COLOR}
                fillOpacity={0.1}
                legendType="none"
                connectNulls
              />
              <Bar yAxisId="left" dataKey="powerValue" name="Power" fill={POWER_COLOR} radius={[6, 6, 0, 0]} />
              <Line
                yAxisId="right"
                type="monotone"
                dataKey="energyPriceDisplay"
                name="Price"
                stroke={PRICE_COLOR}
                strokeWidth={4}
                dot={false}
                connectNulls
              />
              <Line
                yAxisId="right"
                type="monotone"
                dataKey="co2IntensityDisplay"
                name="CO2"
                stroke={CO2_COLOR}
                strokeWidth={4}
                dot={false}
                connectNulls
              />
            </ComposedChart>
          </ResponsiveContainer>
        </div>
        <p className="mt-2 px-1 text-xs text-muted-foreground">
          Display normalized for comparison. Tooltip values remain raw units.
        </p>
      </CardContent>
    </Card>
  )
}
