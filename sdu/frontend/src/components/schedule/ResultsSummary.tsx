import { BanknoteArrowDown, Leaf } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { type ScheduleResult } from '@/types/api'

interface ResultsSummaryProps {
  result: ScheduleResult | null
  isLoading: boolean
}

const currencyFormatter = new Intl.NumberFormat('en-DK', {
  style: 'currency',
  currency: 'DKK',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const numberFormatter = new Intl.NumberFormat('en-DK', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 0,
})

export function ResultsSummary({ result, isLoading }: ResultsSummaryProps) {
  const costText = result ? currencyFormatter.format(result.totalCost) : '--'
  const co2Text = result ? `${numberFormatter.format(result.totalCO2)} gCO2` : '--'

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="flex items-center gap-2 text-base sm:text-lg">
            <BanknoteArrowDown className="h-4 w-4 text-primary" />
            Estimated Total Cost
          </CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-muted-foreground">
              <Spinner className="h-4 w-4" />
              Calculating...
            </div>
          ) : (
            <p className="font-display text-2xl font-semibold text-foreground sm:text-3xl">{costText}</p>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="flex items-center gap-2 text-base sm:text-lg">
            <Leaf className="h-4 w-4 text-primary" />
            Total Carbon Footprint
          </CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-muted-foreground">
              <Spinner className="h-4 w-4" />
              Calculating...
            </div>
          ) : (
            <p className="font-display text-2xl font-semibold text-foreground sm:text-3xl">{co2Text}</p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
