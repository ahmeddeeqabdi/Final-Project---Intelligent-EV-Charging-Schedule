import { useEffect, useMemo, useState } from 'react'
import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { type ScheduledSlot } from '@/types/api'

interface ScheduleChartProps {
  slots: ScheduledSlot[]
  isLoading: boolean
  windowStart?: string | null
  windowEnd?: string | null
}

interface ChartPoint {
  timestamp: string
  timeLabel: string
  powerValue: number
  energyPrice: number | null
  co2Intensity: number | null
}

const twoDecimal = new Intl.NumberFormat('en-DK', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const integerFormatter = new Intl.NumberFormat('en-DK', {
  maximumFractionDigits: 0,
})

const toLabel = (timestamp: string): string => {
  const date = new Date(timestamp)
  return new Intl.DateTimeFormat('en-DK', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
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

export function ScheduleChart({ slots, isLoading, windowStart = null, windowEnd = null }: ScheduleChartProps) {
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
    if (!slots.length) {
      return []
    }

    const slotByHour = new Map<string, { power: number; price: number; co2: number }>()
    for (const slot of slots) {
      const key = new Date(slot.timestamp).toISOString().slice(0, 13)
      const current = slotByHour.get(key)
      if (current) {
        current.power += slot.powerValue
        current.price = slot.energyPrice
        current.co2 = slot.co2Intensity
      } else {
        slotByHour.set(key, {
          power: slot.powerValue,
          price: slot.energyPrice,
          co2: slot.co2Intensity,
        })
      }
    }

    const sortedSlots = [...slots].sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime())
    const inferredStart = new Date(sortedSlots[0].timestamp)
    const inferredEnd = new Date(sortedSlots[sortedSlots.length - 1].timestamp)
    inferredEnd.setHours(inferredEnd.getHours() + 1)

    const explicitStart = windowStart ? new Date(windowStart) : null
    const explicitEnd = windowEnd ? new Date(windowEnd) : null

    const start = explicitStart && !Number.isNaN(explicitStart.getTime()) ? explicitStart : inferredStart
    const end = explicitEnd && !Number.isNaN(explicitEnd.getTime()) ? explicitEnd : inferredEnd

    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || end <= start) {
      return sortedSlots.map((slot) => ({
        timestamp: slot.timestamp,
        timeLabel: toLabel(slot.timestamp),
        powerValue: slot.powerValue,
        energyPrice: slot.energyPrice,
        co2Intensity: slot.co2Intensity,
      }))
    }

    const cursor = new Date(start)
    cursor.setMinutes(0, 0, 0)
    const points: ChartPoint[] = []

    while (cursor < end) {
      const timestamp = cursor.toISOString()
      const key = timestamp.slice(0, 13)
      const slot = slotByHour.get(key)

      points.push({
        timestamp,
        timeLabel: toLabel(timestamp),
        powerValue: slot?.power ?? 0,
        energyPrice: slot?.price ?? null,
        co2Intensity: slot?.co2 ?? null,
      })

      cursor.setHours(cursor.getHours() + 1)
    }

    return points
  }, [slots, windowStart, windowEnd])

  const tickInterval = useMemo(() => getInterval(viewportWidth, chartData.length), [viewportWidth, chartData.length])

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
              <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
              <XAxis dataKey="timeLabel" interval={tickInterval} tick={{ fontSize: 12 }} minTickGap={14} />
              <YAxis
                yAxisId="left"
                tick={{ fontSize: 12 }}
                tickFormatter={(value: number) => `${integerFormatter.format(value)} kW`}
                width={58}
              />
              <YAxis
                yAxisId="right"
                orientation="right"
                tick={{ fontSize: 12 }}
                tickFormatter={(value: number) => twoDecimal.format(value)}
                width={64}
              />
              <Tooltip
                formatter={(value: number | string | null, name: string | number) => {
                  if (value == null) {
                    return ['N/A', String(name)]
                  }
                  const numericValue = typeof value === 'number' ? value : Number(value ?? 0)
                  const seriesName = String(name)

                  if (seriesName === 'Power') {
                    return [`${twoDecimal.format(numericValue)} kW`, seriesName]
                  }
                  if (seriesName === 'Price') {
                    return [`${twoDecimal.format(numericValue)} DKK/kWh`, seriesName]
                  }
                  return [`${twoDecimal.format(numericValue)} gCO2/kWh`, seriesName]
                }}
              />
              <Legend />
              <Bar yAxisId="left" dataKey="powerValue" name="Power" fill="hsl(var(--primary))" radius={[6, 6, 0, 0]} />
              <Line
                yAxisId="right"
                type="monotone"
                dataKey="energyPrice"
                name="Price"
                stroke="#0d9488"
                strokeWidth={2}
                dot={false}
              />
              <Line
                yAxisId="right"
                type="monotone"
                dataKey="co2Intensity"
                name="CO2"
                stroke="#f59e0b"
                strokeWidth={2}
                dot={false}
              />
            </ComposedChart>
          </ResponsiveContainer>
        </div>
      </CardContent>
    </Card>
  )
}
