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
      ? `${Math.max(0, Math.round(result.fallbackDataAgeHours))} h old`
      : 'of unknown age'

  return (
    <div role="status" className="flex items-start gap-3 rounded-lg border border-warning/60 bg-warning/15 p-4">
      <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-warning-foreground dark:text-warning" />
      <div className="min-w-0 flex-1 space-y-1 text-sm">
        <p className="font-semibold text-foreground">Live market data is unavailable — using cached prices</p>
        <p className="text-muted-foreground">
          The plan uses {result.fallbackSource} data that is {dataAgeText}
          {result.fallbackReason ? ` (${result.fallbackReason})` : ''}. It is still usable, but may be less accurate than a live plan.
        </p>
      </div>
      {onDismiss ? (
        <button
          type="button"
          onClick={onDismiss}
          className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-warning/20 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          aria-label="Dismiss cached data notice"
          title="Dismiss"
        >
          <X className="h-4 w-4" />
        </button>
      ) : null}
    </div>
  )
}
