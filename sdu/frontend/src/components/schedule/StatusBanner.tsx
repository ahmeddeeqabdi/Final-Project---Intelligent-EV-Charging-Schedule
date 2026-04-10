import { AlertTriangle } from 'lucide-react'
import { type ScheduleResult } from '@/types/api'

interface StatusBannerProps {
  result: ScheduleResult | null
}

export function StatusBanner({ result }: StatusBannerProps) {
  if (!result?.isDegradedMode) {
    return null
  }

  return (
    <div className="sticky top-0 z-50 border-b border-warning-foreground/25 bg-warning px-4 py-3 text-warning-foreground shadow">
      <div className="mx-auto flex max-w-7xl items-center gap-2 text-sm font-medium sm:px-2">
        <AlertTriangle className="h-4 w-4 shrink-0" />
        <p>
          Using historical energy profiles (EDS API offline). Source: <span className="font-semibold">{result.fallbackSource}</span>
          {result.fallbackReason ? ` (${result.fallbackReason})` : ''}.
        </p>
      </div>
    </div>
  )
}
