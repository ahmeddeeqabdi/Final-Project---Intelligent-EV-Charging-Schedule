import { AlertTriangle, X } from 'lucide-react'
import { type ScheduleResult } from '@/types/api'

interface StatusBannerProps {
  result: ScheduleResult | null
  onDismiss?: () => void
}

export function StatusBanner({ result, onDismiss }: StatusBannerProps) {
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
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-2 text-sm font-medium">
            <AlertTriangle className="h-4 w-4 shrink-0" />
            <p>
              Using historical energy profiles (EDS API offline). Source:{' '}
              <span className="font-semibold">{result.fallbackSource}</span>
              {result.fallbackReason ? ` (${result.fallbackReason})` : ''}.
            </p>
          </div>
          {onDismiss ? (
            <button
              type="button"
              onClick={onDismiss}
              className="inline-flex min-h-8 min-w-8 items-center justify-center rounded-md border border-warning-foreground/25 text-warning-foreground/90 transition-colors hover:bg-warning-foreground/10 hover:text-warning-foreground"
              aria-label="Dismiss degraded mode notice"
              title="Dismiss"
            >
              <X className="h-4 w-4" />
            </button>
          ) : null}
        </div>
        <p className="text-xs font-medium">
          What this means: schedule quality may be lower than live mode. Data freshness: {dataAgeText}.
        </p>
      </div>
    </div>
  )
}
