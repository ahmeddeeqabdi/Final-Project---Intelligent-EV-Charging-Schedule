import { Check, Sparkles } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { OptimizationAlgorithm, ScheduleComparison } from '@/types/api'

const labels: Record<OptimizationAlgorithm, string> = {
  naive: 'Charge immediately', greedy: 'Balanced and fast', optimal: 'Best overall balance', mip: 'Most precise',
}
const technicalLabels: Record<OptimizationAlgorithm, string> = {
  naive: 'Naive baseline', greedy: 'Greedy heuristic', optimal: 'Dynamic programming', mip: 'Mixed-integer programming',
}

interface Props {
  comparisons: ScheduleComparison
  active: OptimizationAlgorithm
  costWeight: number
  onSelect: (algorithm: OptimizationAlgorithm) => void
}

export function StrategyComparison({ comparisons, active, costWeight, onSelect }: Props) {
  const entries = (Object.entries(comparisons) as Array<[OptimizationAlgorithm, NonNullable<ScheduleComparison[OptimizationAlgorithm]>]>)
  if (entries.length < 2) return null

  const costs = entries.map(([, result]) => result.totalCost)
  const emissions = entries.map(([, result]) => result.totalCO2)
  const range = (values: number[]) => Math.max(...values) - Math.min(...values) || 1
  const costMin = Math.min(...costs)
  const emissionMin = Math.min(...emissions)
  const recommended = entries.reduce((best, current) => {
    const score = ([, result]: typeof current) =>
      costWeight * ((result.totalCost - costMin) / range(costs)) +
      (1 - costWeight) * ((result.totalCO2 - emissionMin) / range(emissions))
    return score(current) < score(best) ? current : best
  })[0]

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-lg">Compare other plans</CardTitle>
        <p className="text-sm text-muted-foreground">Select an alternative to update the results below. Hover or focus a plan to see its technical method.</p>
      </CardHeader>
      <CardContent className="grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
        {entries.map(([algorithm, result]) => (
          <Button
            key={algorithm}
            type="button"
            variant="secondary"
            className={`h-auto min-h-28 justify-start p-3 text-left ${active === algorithm ? 'border-2 border-primary bg-primary/10' : ''}`}
            title={`Technical method: ${technicalLabels[algorithm]}`}
            onClick={() => onSelect(algorithm)}
          >
            <span className="w-full space-y-1">
              <span className="flex items-center justify-between font-bold">
                {labels[algorithm]}
                {active === algorithm ? <Check className="h-4 w-4 text-primary" /> : null}
              </span>
              {recommended === algorithm ? (
                <span className="flex items-center gap-1 text-xs font-bold text-success"><Sparkles className="h-3 w-3" />Recommended</span>
              ) : null}
              {active === algorithm ? <span className="block text-xs font-medium text-primary">Currently viewing</span> : null}
              <span className="block text-xs text-muted-foreground">{result.totalCost.toFixed(2)} DKK</span>
              <span className="block text-xs text-muted-foreground">{Math.round(result.totalCO2)} gCO2</span>
            </span>
          </Button>
        ))}
      </CardContent>
    </Card>
  )
}
