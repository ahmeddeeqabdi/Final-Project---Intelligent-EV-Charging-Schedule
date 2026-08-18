import { useState } from 'react'
import { Bell, CalendarPlus, Download } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { createChargingReminder, downloadScheduleCalendar, downloadScheduleCsv } from '@/lib/schedule-export'
import type { ScheduleResult } from '@/types/api'

export function ScheduleActions({ result }: { result: ScheduleResult | null }) {
  const [message, setMessage] = useState<string | null>(null)
  if (!result) return null

  return (
    <div className="flex flex-wrap items-center gap-2 border-y border-border py-3">
      <span className="mr-1 text-xs font-bold uppercase tracking-wider text-muted-foreground">Use this plan</span>
      <Button type="button" variant="secondary" className="min-h-9 px-3" onClick={() => downloadScheduleCsv(result)}>
        <Download className="mr-2 h-4 w-4" />CSV
      </Button>
      <Button type="button" variant="secondary" className="min-h-9 px-3" onClick={() => downloadScheduleCalendar(result)}>
        <CalendarPlus className="mr-2 h-4 w-4" />Calendar
      </Button>
      <Button type="button" variant="secondary" className="min-h-9 px-3" onClick={() => void createChargingReminder(result).then(setMessage)}>
        <Bell className="mr-2 h-4 w-4" />Remind me
      </Button>
      {message ? <p className="w-full text-xs text-muted-foreground">{message}</p> : null}
    </div>
  )
}
