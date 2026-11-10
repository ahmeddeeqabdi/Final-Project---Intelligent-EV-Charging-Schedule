import { useState } from 'react'
import { Clock3, Download } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useScheduleHistory } from '@/hooks/useScheduleHistory'
import { formatCo2, formatDayTime, formatDkk, formatRelative, strategyInfo } from '@/lib/format'
import { downloadScheduleCsv } from '@/lib/schedule-export'

const COLLAPSED_COUNT = 3

export function ScheduleHistory() {
  const history = useScheduleHistory()
  const [expanded, setExpanded] = useState(false)
  if (!history.data?.length) return null

  const items = history.data.slice(0, expanded ? 9 : COLLAPSED_COUNT)
  const hasMore = history.data.length > COLLAPSED_COUNT

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0 pb-3">
        <CardTitle className="flex items-center gap-2 text-lg"><Clock3 className="h-5 w-5 text-primary" />Recent plans</CardTitle>
        {hasMore ? (
          <Button type="button" variant="ghost" className="min-h-9 px-3 text-sm" onClick={() => setExpanded((value) => !value)} aria-expanded={expanded}>
            {expanded ? 'Show fewer' : `Show all (${Math.min(history.data.length, 9)})`}
          </Button>
        ) : null}
      </CardHeader>
      <CardContent>
        <ul className="divide-y divide-border overflow-hidden rounded-md border border-border bg-background">
          {items.map((item) => (
            <li key={item.id} className="flex items-center gap-3 p-3">
              <div className="min-w-0 flex-1">
                <p className="truncate font-semibold">{strategyInfo[item.algorithm]?.label ?? item.algorithm}</p>
                <p className="text-xs text-muted-foreground" title={formatDayTime(item.createdAt)}>{formatRelative(item.createdAt)}</p>
              </div>
              <div className="text-right text-sm tabular-nums">
                <p className="font-semibold">{formatDkk(item.totalCost)}</p>
                <p className="text-xs text-muted-foreground">{formatCo2(item.totalCO2)}</p>
              </div>
              <Button type="button" variant="ghost" className="min-h-10 w-10 shrink-0 px-0" aria-label={`Download plan from ${formatDayTime(item.createdAt)} as CSV`} title="Download CSV" onClick={() => downloadScheduleCsv(item)}>
                <Download className="h-4 w-4" />
              </Button>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  )
}
