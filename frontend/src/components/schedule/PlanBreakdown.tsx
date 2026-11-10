import { formatCo2, formatClock, formatDkk, formatKwh, formatPricePerKwh } from '@/lib/format'
import { type ScheduleResult } from '@/types/api'

const HOUR_MS = 3_600_000
const cell = 'whitespace-nowrap px-3 py-3 text-right'

export function PlanBreakdown({ result }: { result: ScheduleResult }) {
  const rows = result.slots
    .map((slot) => ({ ...slot, startMs: new Date(slot.timestamp).getTime() }))
    .filter((slot) => slot.powerValue >= 0.01 && !Number.isNaN(slot.startMs))
    .sort((a, b) => a.startMs - b.startMs)

  if (!rows.length) {
    return <p className="text-sm text-muted-foreground">This plan has no charging hours — your battery already meets the target.</p>
  }

  const total = (pick: (row: (typeof rows)[number]) => number) => rows.reduce((sum, row) => sum + pick(row), 0)

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full border-collapse text-sm tabular-nums">
        <thead className="border-b border-border text-xs uppercase tracking-wide text-muted-foreground">
          <tr>
            <th scope="col" className="py-2 pr-3 text-left font-semibold">Hour</th>
            {['Power', 'Energy', 'Price', 'CO₂ intensity', 'Cost'].map((label) => (
              <th key={label} scope="col" className="px-3 py-2 text-right font-semibold">{label}</th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {rows.map((row) => (
            <tr key={row.startMs}>
              <th scope="row" className="whitespace-nowrap py-3 pr-3 text-left font-medium">{formatClock(row.startMs)}–{formatClock(row.startMs + HOUR_MS)}</th>
              <td className={cell}>{row.powerValue.toFixed(1)} kW</td>
              <td className={cell}>{formatKwh(row.powerValue)}</td>
              <td className={cell}>{formatPricePerKwh(row.energyPrice)}</td>
              <td className={cell}>{Math.round(row.co2Intensity)} g/kWh</td>
              <td className={`${cell} font-semibold`}>{formatDkk(row.powerValue * row.energyPrice)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr className="border-t-2 border-foreground font-bold">
            <th scope="row" className="py-3 pr-3 text-left">Total</th>
            <td className={cell} />
            <td className={cell}>{formatKwh(total((row) => row.powerValue))}</td>
            <td className={cell} />
            <td className={cell}>{formatCo2(total((row) => row.powerValue * row.co2Intensity))}</td>
            <td className={cell}>{formatDkk(total((row) => row.powerValue * row.energyPrice))}</td>
          </tr>
        </tfoot>
      </table>
    </div>
  )
}
