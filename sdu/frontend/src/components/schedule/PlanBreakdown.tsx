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
        <CardHeader className="pb-2">
          <CardTitle className="text-base sm:text-lg">Plan Breakdown</CardTitle>
          <CardDescription>Creating a detailed slot-by-slot explanation...</CardDescription>
        </CardHeader>
        <CardContent className="flex min-h-[140px] items-center justify-center">
          <div className="inline-flex min-h-11 items-center gap-2 text-base text-muted-foreground">
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
        <CardHeader className="pb-2">
          <CardTitle className="text-base sm:text-lg">Plan Breakdown</CardTitle>
          <CardDescription>
            Get a slot-level plan table after generating a charging plan.
          </CardDescription>
        </CardHeader>
        <CardContent className="text-base text-muted-foreground">
          No charging slots to explain yet. Try generating a schedule with a future departure time.
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="pt-4 lg:pr-2">
      <div className="mb-4">
        <h3 className="font-display text-[2.1rem] font-bold tracking-tight text-black">Plan Breakdown</h3>
        <p className="mt-1 font-sans text-[1.05rem] text-black">
          Slot-by-slot economics and sustainability for the selected charging windows.
        </p>
      </div>
      <div>
        <div className="overflow-x-auto">
          <table className="min-w-full border-collapse text-[1.05rem] tabular-nums">
            <thead className="border-b-2 border-foreground uppercase tracking-[0.1em] text-black">
              <tr>
                <th className="py-3 pr-4 text-left font-semibold">Window</th>
                <th className="py-3 px-4 text-right font-semibold">Power</th>
                <th className="py-3 px-4 text-right font-semibold">Energy</th>
                <th className="py-3 px-4 text-right font-semibold">Price</th>
                <th className="py-3 px-4 text-right font-semibold">CO2</th>
                <th className="py-3 pl-4 text-right font-semibold">Cost</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#E0DDD5]">
              {rows.map((row) => (
                <tr key={row.timestampMs}>
                  <td className="whitespace-nowrap py-3 pr-4 text-left font-medium text-black">
                    {row.startLabel} - {row.endLabel}
                  </td>
                  <td className="whitespace-nowrap py-3 px-4 text-right text-black">
                    {decimalFormatter.format(row.powerKw)} kW
                  </td>
                  <td className="whitespace-nowrap py-3 px-4 text-right text-black">
                    {decimalFormatter.format(row.energyKwh)} kWh
                  </td>
                  <td className="whitespace-nowrap py-3 px-4 text-right text-black">
                    {decimalFormatter.format(row.priceDkkPerKwh)} kr/kWh
                  </td>
                  <td className="whitespace-nowrap py-3 px-4 text-right text-black">
                    {decimalFormatter.format(row.co2PerKwh)} g/kWh
                  </td>
                  <td className="whitespace-nowrap py-3 pl-4 text-right font-semibold text-black">
                    {currencyFormatter.format(row.costDkk)}
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot className="text-black">
              <tr className="border-t-2 border-foreground font-bold text-black">
                <td className="py-4 pr-4 text-left text-[1.05rem] uppercase tracking-[0.1em]">
                  Totals
                </td>
                <td className="py-4 px-4 text-right">-</td>
                <td className="whitespace-nowrap py-4 px-4 text-right">
                  {decimalFormatter.format(totals.energy)} kWh
                </td>
                <td className="py-4 px-4 text-right">-</td>
                <td className="whitespace-nowrap py-4 px-4 text-right">
                  {decimalFormatter.format(totals.co2)} gCO2
                </td>
                <td className="whitespace-nowrap py-4 pl-4 text-right text-[1.15rem]">
                  {currencyFormatter.format(totals.cost)}
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>
    </div>
  )
}
