import { BatteryCharging, CheckCircle2, Clock3, Coins, Leaf } from 'lucide-react'
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
    <div className="space-y-3" aria-live="polite">
      {isLoading ? <div className="inline-flex min-h-24 w-full items-center justify-center gap-2 rounded-lg border border-border"><Spinner />Building your plan…</div> : null}
      {result && !isLoading ? <div className="flex items-center gap-3 rounded-lg border border-success/30 bg-success/10 p-4"><span className="grid h-10 w-10 place-items-center rounded-full bg-success text-success-foreground"><CheckCircle2 className="h-5 w-5" /></span><div><p className="font-semibold text-success">Your charging plan is ready</p><p className="text-sm text-muted-foreground">Charging is scheduled to finish by {readyText}.</p></div></div> : null}
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      {!isLoading ? [
        { icon: Coins, label: 'Estimated cost', value: costText, note: savings > 0.005 ? `${currencyFormatter.format(savings)} saved versus charging now` : 'For this charging session', positive: savings > 0.005 },
        { icon: Leaf, label: 'Carbon footprint', value: co2Text, note: 'Estimated charging emissions', positive: false },
        { icon: Clock3, label: 'Start charging', value: startText, note: `Ready by ${readyText}`, positive: true },
        { icon: BatteryCharging, label: 'Battery at departure', value: result ? `${Math.round(finalSoc)}%` : '--', note: `Your target is ${targetSoC}%`, positive: true },
      ].map(({ icon: Icon, label, value, note, positive }) => (
        <div key={label} className={`rounded-lg border bg-card p-4 shadow-sm ${positive ? 'border-success/35' : 'border-border'}`}>
          <p className="flex items-center gap-2 text-sm font-medium text-muted-foreground"><Icon className={`h-4 w-4 ${positive ? 'text-success' : 'text-primary'}`} />{label}</p>
          <p className="mt-2 font-display text-2xl font-bold text-foreground">{value}</p>
          <p className={`mt-1 text-xs ${positive ? 'text-success' : 'text-muted-foreground'}`}>{note}</p>
        </div>
      )) : null}
      </div>
    </div>
  )
}
