import { AlertTriangle } from 'lucide-react'
import { type ScheduleResult } from '@/types/api'

interface StatusBannerProps {
  result: ScheduleResult | null
}

export function StatusBanner({ result }: StatusBannerProps) {
  if (!result?.isDegradedMode) {
    return null
  }

  const dataAgeText =
    typeof result.fallbackDataAgeHours === 'number'
      ? `${Math.max(0, Math.round(result.fallbackDataAgeHours))}h old`
      : 'age unknown'

  return (
    <div className="sticky top-0 z-50 border-b border-warning-foreground/25 bg-warning px-4 py-3 text-warning-foreground shadow">
      <div className="mx-auto max-w-7xl space-y-1 sm:px-2">
        <div className="flex items-center gap-2 text-sm font-medium">
          <AlertTriangle className="h-4 w-4 shrink-0" />
          <p>
            Using historical energy profiles (EDS API offline). Source:{' '}
            <span className="font-semibold">{result.fallbackSource}</span>
            {result.fallbackReason ? ` (${result.fallbackReason})` : ''}.
          </p>
        </div>
        <p className="text-xs font-medium">
          What this means: schedule quality may be lower than live mode. Data freshness: {dataAgeText}.
        </p>
      </div>
    </div>
  )
}
