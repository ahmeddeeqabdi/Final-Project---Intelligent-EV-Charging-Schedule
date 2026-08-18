import { BatteryCharging, Clock3, Coins, Leaf } from 'lucide-react'
import { Spinner } from '@/components/ui/spinner'
import { type ScheduleResult } from '@/types/api'

interface ResultsSummaryProps {
  result: ScheduleResult | null
  baseline?: ScheduleResult | null
  currentSoC?: number
  targetSoC?: number
  batteryCapacity?: number
  isLoading: boolean
}

const currencyFormatter = new Intl.NumberFormat('en-DK', {
  style: 'currency',
  currency: 'DKK',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const numberFormatter = new Intl.NumberFormat('en-DK', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 0,
})

const timeFormatter = new Intl.DateTimeFormat('en-DK', { weekday: 'short', hour: '2-digit', minute: '2-digit' })

export function ResultsSummary({ result, baseline, currentSoC = 0, targetSoC = 0, batteryCapacity = 1, isLoading }: ResultsSummaryProps) {
  const costText = result ? currencyFormatter.format(result.totalCost) : '--'
  const co2Text = result ? `${numberFormatter.format(result.totalCO2)} gCO2` : '--'
  const chargingSlots = result?.slots.filter((slot) => slot.powerValue > 0).sort((a, b) => a.timestamp.localeCompare(b.timestamp)) ?? []
  const energy = chargingSlots.reduce((sum, slot) => sum + slot.powerValue, 0)
  const finalSoc = result ? Math.min(targetSoC, currentSoC + (energy / batteryCapacity) * 100) : 0
  const startText = chargingSlots[0] ? timeFormatter.format(new Date(chargingSlots[0].timestamp)) : '--'
  const readyText = chargingSlots.length
    ? timeFormatter.format(new Date(new Date(chargingSlots[chargingSlots.length - 1].timestamp).getTime() + 3_600_000))
    : '--'
  const savings = result && baseline ? Math.max(0, baseline.totalCost - result.totalCost) : 0

  if (!result && !isLoading) {
    return <div className="border border-dashed border-border bg-card p-5"><p className="font-bold">Your charging plan will appear here</p><p className="mt-1 text-sm text-muted-foreground">Enter your battery level and departure time, then build a plan.</p></div>
  }

  return (
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      {isLoading ? <div className="col-span-full inline-flex min-h-24 items-center justify-center gap-2 border border-border"><Spinner />Building your plan…</div> : null}
      {!isLoading ? [
        { icon: Coins, label: 'Estimated cost', value: costText, note: savings > 0.005 ? `${currencyFormatter.format(savings)} less than charging now` : 'For this charging session' },
        { icon: Leaf, label: 'Carbon footprint', value: co2Text, note: result ? `${result.algorithm} strategy` : '—' },
        { icon: Clock3, label: 'Start charging', value: startText, note: `Ready by ${readyText}` },
        { icon: BatteryCharging, label: 'Expected battery', value: result ? `${Math.round(finalSoc)}%` : '--', note: `Target ${targetSoC}%` },
      ].map(({ icon: Icon, label, value, note }) => (
        <div key={label} className="border border-border bg-card p-4 shadow-hard-sm">
          <p className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-muted-foreground"><Icon className="h-4 w-4" />{label}</p>
          <p className="mt-2 font-display text-2xl font-bold text-foreground">{value}</p>
          <p className="mt-1 text-xs text-muted-foreground">{note}</p>
        </div>
      )) : null}
    </div>
  )
}
