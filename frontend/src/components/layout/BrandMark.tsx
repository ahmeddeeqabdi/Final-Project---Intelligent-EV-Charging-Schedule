import { Zap } from 'lucide-react'
import { cn } from '@/lib/utils'

export function BrandMark({ className, compact = false }: { className?: string; compact?: boolean }) {
  return (
    <span className={cn('inline-flex items-center gap-2.5', className)}>
      <span className="grid h-9 w-9 shrink-0 place-items-center rounded-md bg-primary text-primary-foreground shadow-sm">
        <Zap className="h-5 w-5" fill="currentColor" aria-hidden="true" />
      </span>
      {compact ? null : (
        <span className="leading-tight">
          <span className="block font-display text-base font-bold tracking-tight text-foreground">EV Charging Scheduler</span>
          <span className="block text-xs text-muted-foreground">Smart EV charging for DK1 &amp; DK2</span>
        </span>
      )}
    </span>
  )
}
