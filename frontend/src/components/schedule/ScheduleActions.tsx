import { useState } from 'react'
import { Bell, CalendarPlus, Download } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { createChargingReminder, downloadScheduleCalendar, downloadScheduleCsv } from '@/lib/schedule-export'
import type { ScheduleResult } from '@/types/api'

export function ScheduleActions({ result }: { result: ScheduleResult | null }) {
  const [message, setMessage] = useState<string | null>(null)
  if (!result) return null

  return (
    <div className="rounded-lg border border-border bg-card p-3 shadow-sm">
      <div className="flex flex-wrap items-center gap-2">
        <span className="mr-auto px-1 text-sm font-semibold text-foreground">Keep this plan</span>
        <Button type="button" variant="secondary" className="min-h-10 px-3" onClick={() => downloadScheduleCalendar(result)}>
          <CalendarPlus className="mr-2 h-4 w-4" />Add to calendar
        </Button>
        <Button type="button" variant="secondary" className="min-h-10 px-3" onClick={() => void createChargingReminder(result).then(setMessage)}>
          <Bell className="mr-2 h-4 w-4" />Remind me
        </Button>
        <Button type="button" variant="secondary" className="min-h-10 px-3" onClick={() => downloadScheduleCsv(result)}>
          <Download className="mr-2 h-4 w-4" />CSV
        </Button>
      </div>
      {message ? <p role="status" className="mt-2 px-1 text-xs text-muted-foreground">{message}</p> : null}
    </div>
  )
}
