import { ThickArrowDownIcon, GlobeIcon } from '@radix-ui/react-icons'
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
    <div className="flex flex-col sm:flex-row gap-6 border-b border-[#E0DDD5] pb-8 mb-8 items-start">
      <div className="flex-1 pr-6 border-r-0 sm:border-r border-[#E0DDD5]">
        <div className="mb-4">
          <h3 className="flex items-center gap-3 text-base tracking-[0.15em] font-semibold uppercase text-muted-foreground">
            <ThickArrowDownIcon className="h-5 w-5 text-foreground" />
            Estimated Total Cost
          </h3>
        </div>
        <div className="min-h-[3rem]">
          {isLoading ? (
            <div className="inline-flex min-h-11 items-center gap-2 text-base font-medium text-muted-foreground">
              <Spinner className="h-4 w-4" />
              Calculating...
            </div>
          ) : (
            <p className="font-display text-4xl sm:text-5xl font-bold tracking-tight text-foreground">{costText}</p>
          )}
        </div>
      </div>

      <div className="flex-1">
        <div className="mb-4">
          <h3 className="flex items-center gap-3 text-base tracking-[0.15em] font-semibold uppercase text-muted-foreground">
            <GlobeIcon className="h-5 w-5 text-foreground" />
            Total Carbon Footprint
          </h3>
        </div>
        <div className="min-h-[3rem]">
          {isLoading ? (
            <div className="inline-flex min-h-11 items-center gap-2 text-base font-medium text-muted-foreground">
              <Spinner className="h-4 w-4" />
              Calculating...
            </div>
          ) : (
            <p className="font-display text-4xl sm:text-5xl font-bold tracking-tight text-foreground">{co2Text}</p>
          )}
        </div>
      </div>
    </div>
  )
}
