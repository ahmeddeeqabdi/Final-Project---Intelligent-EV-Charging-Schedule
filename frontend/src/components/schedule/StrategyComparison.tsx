import { Check, Sparkles } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatCo2, formatDkk, strategyInfo } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { OptimizationAlgorithm, ScheduleComparison, ScheduleResult } from '@/types/api'

interface Props {
  comparisons: ScheduleComparison
  active: OptimizationAlgorithm
  costWeight: number
  onSelect: (algorithm: OptimizationAlgorithm) => void
}

const order: OptimizationAlgorithm[] = ['optimal', 'mip', 'greedy', 'naive']

export function StrategyComparison({ comparisons, active, costWeight, onSelect }: Props) {
  const entries = order
    .filter((algorithm) => comparisons[algorithm])
    .map((algorithm) => [algorithm, comparisons[algorithm]] as [OptimizationAlgorithm, ScheduleResult])
  if (entries.length < 2) return null

  const costs = entries.map(([, result]) => result.totalCost)
  const emissions = entries.map(([, result]) => result.totalCO2)
  const range = (values: number[]) => Math.max(...values) - Math.min(...values) || 1
  const costMin = Math.min(...costs)
  const costMax = Math.max(...costs)
  const emissionMin = Math.min(...emissions)
  const score = ([, result]: [OptimizationAlgorithm, ScheduleResult]) =>
    costWeight * ((result.totalCost - costMin) / range(costs)) +
    (1 - costWeight) * ((result.totalCO2 - emissionMin) / range(emissions))
  const recommended = entries.reduce((best, current) => (score(current) < score(best) ? current : best))[0]
  const baseline = comparisons.naive

  return (
    <Card className="animate-rise">
      <CardHeader className="pb-3">
        <CardTitle className="text-lg">Compare planning methods</CardTitle>
        <CardDescription>Select a method to update the plan, timeline, and breakdown.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-2 sm:grid-cols-2 2xl:grid-cols-4" role="group" aria-label="Planning methods">
        {entries.map(([algorithm, result]) => {
          const isActive = active === algorithm
          const delta = baseline && algorithm !== 'naive' ? baseline.totalCost - result.totalCost : null
          return (
            <button
              key={algorithm}
              type="button"
              aria-pressed={isActive}
              onClick={() => onSelect(algorithm)}
              className={cn(
                'relative flex min-h-[7.5rem] flex-col rounded-md border p-3.5 text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 ring-offset-background',
                isActive ? 'border-primary bg-primary/10 shadow-sm' : 'border-border bg-background hover:border-foreground/30 hover:bg-secondary/60',
              )}
            >
              <span className="flex items-start justify-between gap-2">
                <span>
                  <span className="block font-semibold text-foreground">{strategyInfo[algorithm].label}</span>
                  <span className="block text-xs text-muted-foreground">{strategyInfo[algorithm].technical}</span>
                </span>
                {isActive ? (
                  <span className="grid h-5 w-5 shrink-0 place-items-center rounded-full bg-primary text-primary-foreground"><Check className="h-3.5 w-3.5" /></span>
                ) : null}
              </span>
              {recommended === algorithm ? (
                <span className="mt-2 inline-flex w-fit items-center gap-1 rounded-full bg-success/15 px-2 py-0.5 text-xs font-semibold text-success">
                  <Sparkles className="h-3 w-3" />
                  <span>Recommended</span>
                </span>
              ) : null}
              <span className="mt-auto pt-3">
                <span className="flex items-baseline justify-between gap-2 tabular-nums">
                  <span className="font-display text-lg font-bold text-foreground">{formatDkk(result.totalCost)}</span>
                  <span className="text-xs text-muted-foreground">{formatCo2(result.totalCO2)}</span>
                </span>
                <span className="mt-1.5 block h-1.5 overflow-hidden rounded-full bg-muted" aria-hidden="true">
                  <span
                    className={cn('block h-full rounded-full', isActive ? 'bg-primary' : 'bg-foreground/40')}
                    style={{ width: `${Math.max(8, (result.totalCost / (costMax || 1)) * 100)}%` }}
                  />
                </span>
                <span className={cn('mt-1.5 block text-xs', delta != null && delta > 0.005 ? 'font-medium text-success' : 'text-muted-foreground')}>
                  {algorithm === 'naive' ? 'Baseline: charge as soon as you plug in' : delta != null && delta > 0.005 ? `${formatDkk(delta)} cheaper than charging now` : 'Same cost as charging now'}
                </span>
              </span>
            </button>
          )
        })}
      </CardContent>
    </Card>
  )
}
