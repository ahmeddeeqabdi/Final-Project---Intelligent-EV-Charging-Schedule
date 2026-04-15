import { type FormEvent, useState } from 'react'
import { Gauge, Leaf, PlugZap } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Slider } from '@/components/ui/slider'
import { Spinner } from '@/components/ui/spinner'
import { cn } from '@/lib/utils'
import { type OptimizationAlgorithm, type PriceZone, type ScheduleFormValues } from '@/types/api'

interface ScheduleFormProps {
  onSubmit: (values: ScheduleFormValues) => void
  onSaveDefaults?: (values: ScheduleFormValues) => void
  initialValues?: Partial<ScheduleFormValues>
  isSubmitting: boolean
  isSavingDefaults?: boolean
}

const toDatetimeLocal = (date: Date): string => {
  const tzOffset = date.getTimezoneOffset() * 60000
  return new Date(date.getTime() - tzOffset).toISOString().slice(0, 16)
}

const getDefaultDeparture = (): string => {
  const date = new Date()
  date.setHours(date.getHours() + 8)
  date.setMinutes(0)
  date.setSeconds(0)
  date.setMilliseconds(0)
  return toDatetimeLocal(date)
}

const buildDefaultValues = (initialValues?: Partial<ScheduleFormValues>): ScheduleFormValues => ({
    batteryCapacity: 77,
    maxPower: 11,
    departureTime: getDefaultDeparture(),
    targetSoC: 80,
    costWeight: 0.5,
    currentSoC: 25,
    priceZone: 'DK2',
    algorithm: 'greedy',
    ...initialValues,
  })

const formatHours = (hours: number): string => {
  if (!Number.isFinite(hours) || hours <= 0) {
    return '0h'
  }

  const wholeHours = Math.floor(hours)
  const minutes = Math.round((hours - wholeHours) * 60)
  if (wholeHours <= 0) {
    return `${minutes}m`
  }
  if (minutes <= 0) {
    return `${wholeHours}h`
  }
  return `${wholeHours}h ${minutes}m`
}

export function ScheduleForm({
  onSubmit,
  onSaveDefaults,
  initialValues,
  isSubmitting,
  isSavingDefaults = false,
}: ScheduleFormProps) {
  const [values, setValues] = useState<ScheduleFormValues>(() => buildDefaultValues(initialValues))

  const now = Date.now()
  const departureTimestamp = new Date(values.departureTime).getTime()
  const energyNeeded = Math.max(0, ((values.targetSoC - values.currentSoC) / 100) * values.batteryCapacity)
  const estimatedHours = values.maxPower > 0 ? energyNeeded / values.maxPower : Number.POSITIVE_INFINITY
  const availableHours = Number.isFinite(departureTimestamp)
    ? Math.max(0, (departureTimestamp - now) / (1000 * 60 * 60))
    : 0

  const validationErrors: string[] = []
  if (!Number.isFinite(departureTimestamp)) {
    validationErrors.push('Departure time must be a valid date and time.')
  } else if (departureTimestamp <= now) {
    validationErrors.push('Departure time must be in the future.')
  }
  if (values.currentSoC > values.targetSoC) {
    validationErrors.push('Current SoC cannot exceed target SoC for charging optimization.')
  }
  if (values.batteryCapacity <= 0 || values.maxPower <= 0) {
    validationErrors.push('Battery capacity and max power must be greater than zero.')
  }
  if (energyNeeded > 0 && Number.isFinite(estimatedHours) && estimatedHours > availableHours) {
    validationErrors.push('The selected target is unlikely to be reachable before departure with current max power.')
  }

  const isFormInvalid = validationErrors.length > 0

  const updateValue = <K extends keyof ScheduleFormValues>(key: K, value: ScheduleFormValues[K]) => {
    setValues((prev) => ({ ...prev, [key]: value }))
  }

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onSubmit(values)
  }

  return (
    <Card className="border-border/70 bg-card/90">
      <CardHeader className="space-y-1">
        <CardTitle className="text-xl">Charging Constraints</CardTitle>
        <CardDescription>Calibrate your schedule for departure readiness and optimization strategy.</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="space-y-4" onSubmit={submit}>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="batteryCapacity" className="inline-flex items-center gap-1.5">
                <PlugZap className="h-4 w-4 text-primary" />
                Battery Size (kWh)
              </Label>
              <Input
                id="batteryCapacity"
                min={1}
                max={200}
                step={0.1}
                type="number"
                value={values.batteryCapacity}
                onChange={(event) => updateValue('batteryCapacity', Number(event.target.value))}
                required
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="maxPower" className="inline-flex items-center gap-1.5">
                <Gauge className="h-4 w-4 text-primary" />
                Max Power (kW)
              </Label>
              <Input
                id="maxPower"
                min={1}
                max={50}
                step={0.1}
                type="number"
                value={values.maxPower}
                onChange={(event) => updateValue('maxPower', Number(event.target.value))}
                required
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="targetSoC">Target State of Charge (%)</Label>
              <Input
                id="targetSoC"
                min={1}
                max={100}
                step={1}
                type="number"
                value={values.targetSoC}
                onChange={(event) => updateValue('targetSoC', Number(event.target.value))}
                required
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="currentSoC">Current SoC (%)</Label>
              <Input
                id="currentSoC"
                min={0}
                max={100}
                step={1}
                type="number"
                value={values.currentSoC}
                onChange={(event) => updateValue('currentSoC', Number(event.target.value))}
                required
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="departureTime">Departure Deadline</Label>
            <Input
              id="departureTime"
              type="datetime-local"
              value={values.departureTime}
              onChange={(event) => updateValue('departureTime', event.target.value)}
              required
            />
          </div>

          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="priceZone">Price Zone</Label>
              <select
                id="priceZone"
                className="flex min-h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                value={values.priceZone}
                onChange={(event) => updateValue('priceZone', event.target.value as PriceZone)}
              >
                <option value="DK1">DK1</option>
                <option value="DK2">DK2</option>
              </select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="algorithm">Optimization Algorithm</Label>
              <select
                id="algorithm"
                className="flex min-h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                value={values.algorithm}
                onChange={(event) =>
                  updateValue('algorithm', event.target.value as OptimizationAlgorithm)
                }
              >
                <option value="greedy">Greedy</option>
                <option value="optimal">Optimal (DP)</option>
                <option value="naive">Naive</option>
              </select>
            </div>
          </div>

          <div className="space-y-2">
            <Label className="inline-flex items-center gap-1.5">
              <Leaf className="h-4 w-4 text-primary" />
              Price vs. Sustainability Weight
            </Label>
            <Slider
              min={0}
              max={1}
              step={0.01}
              value={[values.costWeight]}
              onValueChange={(sliderValue) => updateValue('costWeight', sliderValue[0] ?? 0.5)}
            />
            <div className="flex items-center justify-between text-xs font-medium text-muted-foreground">
              <span>0.0 Min CO2</span>
              <span>{values.costWeight.toFixed(2)}</span>
              <span>1.0 Min Cost</span>
            </div>
          </div>

          <div className="space-y-1.5 rounded-md border border-border/70 bg-muted/30 p-3 text-xs text-muted-foreground">
            <p>
              Energy needed: <span className="font-semibold text-foreground">{energyNeeded.toFixed(1)} kWh</span>
            </p>
            <p>
              Estimated charging duration at max power:{' '}
              <span className="font-semibold text-foreground">{formatHours(estimatedHours)}</span>
            </p>
            <p>
              Time remaining until departure:{' '}
              <span className="font-semibold text-foreground">{formatHours(availableHours)}</span>
            </p>
          </div>

          {validationErrors.length ? (
            <div className="space-y-1.5 rounded-md border border-warning/50 bg-warning/20 p-3 text-xs text-warning-foreground">
              {validationErrors.map((error) => (
                <p key={error}>{error}</p>
              ))}
            </div>
          ) : null}

          <Button
            type="submit"
            className={cn('w-full min-h-11 text-sm sm:text-base')}
            disabled={isSubmitting || isFormInvalid}
          >
            {isSubmitting ? (
              <span className="inline-flex items-center gap-2">
                <Spinner className="h-4 w-4 text-primary-foreground" />
                Building schedule...
              </span>
            ) : (
              'Generate Charging Plan'
            )}
          </Button>

          <Button
            type="button"
            variant="secondary"
            className={cn('w-full min-h-11 text-sm sm:text-base')}
            disabled={isSavingDefaults}
            onClick={() => onSaveDefaults?.(values)}
          >
            {isSavingDefaults ? 'Saving defaults...' : 'Save As Defaults'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
