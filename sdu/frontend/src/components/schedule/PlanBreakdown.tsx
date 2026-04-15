import { useMemo } from 'react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { type ScheduleResult, type ScheduledSlot } from '@/types/api'

interface PlanBreakdownProps {
  result: ScheduleResult | null
  isLoading: boolean
}

interface SlotRow {
  startLabel: string
  endLabel: string
  powerKw: number
  energyKwh: number
  priceDkkPerKwh: number
  co2PerKwh: number
  costDkk: number
  co2Total: number
  timestampMs: number
}

const MIN_CHARGING_POWER_KW = 0.01

const currencyFormatter = new Intl.NumberFormat('en-DK', {
  style: 'currency',
  currency: 'DKK',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const decimalFormatter = new Intl.NumberFormat('en-DK', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const toTimeLabel = (timestamp: string): string => {
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) {
    return '--:--'
  }

  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${hours}:${minutes}`
}

const toSlotRow = (slot: ScheduledSlot): SlotRow | null => {
  const parsed = new Date(slot.timestamp)
  const startMs = parsed.getTime()
  if (Number.isNaN(startMs)) {
    return null
  }

  const end = new Date(startMs)
  end.setHours(end.getHours() + 1)

  const energyKwh = Math.max(0, slot.powerValue)
  const costDkk = energyKwh * slot.energyPrice
  const co2Total = energyKwh * slot.co2Intensity

  return {
    startLabel: toTimeLabel(slot.timestamp),
    endLabel: toTimeLabel(end.toISOString()),
    powerKw: slot.powerValue,
    energyKwh,
    priceDkkPerKwh: slot.energyPrice,
    co2PerKwh: slot.co2Intensity,
    costDkk,
    co2Total,
    timestampMs: startMs,
  }
}

export function PlanBreakdown({ result, isLoading }: PlanBreakdownProps) {
  const rows = useMemo<SlotRow[]>(() => {
    if (!result?.slots?.length) {
      return []
    }

    return result.slots
      .filter((slot) => slot.powerValue >= MIN_CHARGING_POWER_KW)
      .map(toSlotRow)
      .filter((row): row is SlotRow => row != null)
      .sort((a, b) => a.timestampMs - b.timestampMs)
  }, [result])

  const totals = useMemo(() => {
    return rows.reduce(
      (acc, row) => {
        acc.energy += row.energyKwh
        acc.cost += row.costDkk
        acc.co2 += row.co2Total
        return acc
      },
      { energy: 0, cost: 0, co2: 0 },
    )
  }, [rows])

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Plan Breakdown</CardTitle>
          <CardDescription>Creating a detailed slot-by-slot explanation...</CardDescription>
        </CardHeader>
        <CardContent className="flex min-h-[180px] items-center justify-center">
          <div className="inline-flex min-h-11 items-center gap-2 text-sm text-muted-foreground">
            <Spinner className="h-5 w-5" />
            Building explanation...
          </div>
        </CardContent>
      </Card>
    )
  }

  if (!result || !rows.length) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Plan Breakdown</CardTitle>
          <CardDescription>
            Get a slot-level plan table after generating a charging plan.
          </CardDescription>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          No charging slots to explain yet. Try generating a schedule with a future departure time.
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">Plan Breakdown</CardTitle>
        <CardDescription>
          Slot-by-slot economics and sustainability for the selected charging windows.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div>
          <section>
            <div className="overflow-x-auto rounded-lg border border-border/60">
              <table className="min-w-full divide-y divide-border/60 text-sm">
                <thead className="bg-muted/30 text-xs uppercase tracking-[0.06em] text-muted-foreground">
                  <tr>
                    <th className="px-3 py-2 text-left">Window</th>
                    <th className="px-3 py-2 text-right">Power</th>
                    <th className="px-3 py-2 text-right">Energy</th>
                    <th className="px-3 py-2 text-right">Price</th>
                    <th className="px-3 py-2 text-right">CO2</th>
                    <th className="px-3 py-2 text-right">Cost</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/50">
                  {rows.map((row) => (
                    <tr key={row.timestampMs} className="bg-card/40">
                      <td className="whitespace-nowrap px-3 py-2 font-medium text-foreground">
                        {row.startLabel} - {row.endLabel}
                      </td>
                      <td className="whitespace-nowrap px-3 py-2 text-right text-foreground">
                        {decimalFormatter.format(row.powerKw)} kW
                      </td>
                      <td className="whitespace-nowrap px-3 py-2 text-right text-foreground">
                        {decimalFormatter.format(row.energyKwh)} kWh
                      </td>
                      <td className="whitespace-nowrap px-3 py-2 text-right text-foreground">
                        {decimalFormatter.format(row.priceDkkPerKwh)} kr/kWh
                      </td>
                      <td className="whitespace-nowrap px-3 py-2 text-right text-foreground">
                        {decimalFormatter.format(row.co2PerKwh)} g/kWh
                      </td>
                      <td className="whitespace-nowrap px-3 py-2 text-right font-medium text-foreground">
                        {currencyFormatter.format(row.costDkk)}
                      </td>
                    </tr>
                  ))}
                </tbody>
                <tfoot className="border-t border-border/60 bg-muted/20">
                  <tr>
                    <td className="px-3 py-2 text-left text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                      Totals
                    </td>
                    <td className="px-3 py-2 text-right text-foreground">-</td>
                    <td className="whitespace-nowrap px-3 py-2 text-right font-semibold text-foreground">
                      {decimalFormatter.format(totals.energy)} kWh
                    </td>
                    <td className="px-3 py-2 text-right text-foreground">-</td>
                    <td className="whitespace-nowrap px-3 py-2 text-right font-semibold text-foreground">
                      {decimalFormatter.format(totals.co2)} gCO2
                    </td>
                    <td className="whitespace-nowrap px-3 py-2 text-right font-semibold text-foreground">
                      {currencyFormatter.format(totals.cost)}
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </section>
        </div>
      </CardContent>
    </Card>
  )
}
