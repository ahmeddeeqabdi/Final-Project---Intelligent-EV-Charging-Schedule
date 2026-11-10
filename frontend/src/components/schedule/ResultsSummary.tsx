import { useState } from 'react'
import { BatteryCharging, CheckCircle2, Clock3, Coins, Leaf, TrendingDown } from 'lucide-react'
import { formatCo2, formatDayTime, formatDkk, formatKwh, strategyInfo } from '@/lib/format'
import { cn } from '@/lib/utils'
import { type ScheduleResult } from '@/types/api'

interface ResultsSummaryProps {
  result: ScheduleResult
  baseline?: ScheduleResult | null
  currentSoC?: number
  targetSoC?: number
  batteryCapacity?: number
}

const HOUR_MS = 3_600_000

export function ResultsSummary({ result, baseline, currentSoC = 0, targetSoC = 0, batteryCapacity = 1 }: ResultsSummaryProps) {
  // Captured once per mount: the plan is built from "now", so a stable reference avoids flicker on re-render.
  const [renderedAt] = useState(Date.now)
  const chargingSlots = result.slots
    .filter((slot) => slot.powerValue > 0)
    .sort((a, b) => a.timestamp.localeCompare(b.timestamp))
  const energy = chargingSlots.reduce((sum, slot) => sum + slot.powerValue, 0)
  const finalSoc = Math.min(targetSoC, currentSoC + (energy / batteryCapacity) * 100)
  const firstStart = chargingSlots[0] ? new Date(chargingSlots[0].timestamp).getTime() : null
  const startsNow = firstStart != null && firstStart <= renderedAt
  const startText = firstStart == null ? 'No charging needed' : startsNow ? 'Now' : formatDayTime(firstStart)
  const readyText = chargingSlots.length
    ? formatDayTime(new Date(chargingSlots[chargingSlots.length - 1].timestamp).getTime() + HOUR_MS)
    : '—'
  const isBaseline = result.algorithm === 'naive'
  const savings = baseline && !isBaseline ? baseline.totalCost - result.totalCost : 0
  const co2Saved = baseline && !isBaseline ? baseline.totalCO2 - result.totalCO2 : 0
  const savingsPct = baseline && baseline.totalCost > 0 ? (savings / baseline.totalCost) * 100 : 0

  const tiles = [
    {
      icon: Coins,
      label: 'Estimated cost',
      value: formatDkk(result.totalCost),
      note: savings > 0.005 ? `${formatDkk(savings)} less than charging now` : 'For this charging session',
      positive: savings > 0.005,
    },
    {
      icon: Leaf,
      label: 'Carbon footprint',
      value: formatCo2(result.totalCO2),
      note: co2Saved > 0.5 ? `${formatCo2(co2Saved)} less than charging now` : 'Estimated grid emissions',
      positive: co2Saved > 0.5,
    },
    { icon: Clock3, label: 'Charging starts', value: startText, note: `Finished by ${readyText}`, positive: false },
    { icon: BatteryCharging, label: 'Battery at departure', value: `${Math.round(finalSoc)}%`, note: `${formatKwh(energy)} added · target ${targetSoC}%`, positive: false },
  ]

  return (
    <div className="space-y-3 animate-rise" aria-live="polite">
      <div className="flex flex-col gap-4 rounded-lg border border-success/30 bg-success/10 p-4 sm:flex-row sm:items-center sm:p-5">
        <span className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-success text-success-foreground">
          <CheckCircle2 className="h-6 w-6" />
        </span>
        <div className="min-w-0 flex-1">
          <p className="font-display text-lg font-bold text-foreground">
            {firstStart == null ? 'Your battery already meets the target' : startsNow ? 'Plug in now — charging starts right away' : `Plug in now — charging starts ${startText}`}
          </p>
          <p className="text-sm text-muted-foreground">
            Ready by {readyText} using <span className="font-medium text-foreground">{strategyInfo[result.algorithm].label.toLowerCase()}</span> planning.
          </p>
        </div>
        {savings > 0.005 ? (
          <span className="inline-flex items-center gap-1.5 self-start rounded-full bg-success px-3 py-1.5 text-sm font-semibold text-success-foreground sm:self-center">
            <TrendingDown className="h-4 w-4" />
            Save {Math.round(savingsPct)}%
          </span>
        ) : null}
      </div>

      <div className="grid grid-cols-2 gap-3 xl:grid-cols-4">
        {tiles.map(({ icon: Icon, label, value, note, positive }) => (
          <div key={label} className="rounded-lg border border-border bg-card p-3.5 shadow-sm sm:p-4">
            <p className="flex items-center gap-2 text-xs font-medium text-muted-foreground sm:text-sm">
              <Icon className="h-4 w-4 shrink-0 text-primary" />
              {label}
            </p>
            <p className="mt-2 font-display text-xl font-bold tabular-nums text-foreground sm:text-2xl">{value}</p>
            <p className={cn('mt-1 text-xs', positive ? 'font-medium text-success' : 'text-muted-foreground')}>{note}</p>
          </div>
        ))}
      </div>
    </div>
  )
}
