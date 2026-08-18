import { type FormEvent, useState } from 'react'
import { DashboardIcon, GlobeIcon, LightningBoltIcon } from '@radix-ui/react-icons'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Slider } from '@/components/ui/slider'
import { Spinner } from '@/components/ui/spinner'
import type { OptimizationAlgorithm, PriceZone, ScheduleFormValues } from '@/types/api'

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
  const [referenceTime, setReferenceTime] = useState(Date.now)

  const departureTimestamp = new Date(values.departureTime).getTime()
  const energyNeeded = Math.max(0, ((values.targetSoC - values.currentSoC) / 100) * values.batteryCapacity)
  const estimatedHours = values.maxPower > 0 ? energyNeeded / values.maxPower : Number.POSITIVE_INFINITY
  const availableHours = Number.isFinite(departureTimestamp)
    ? Math.max(0, (departureTimestamp - referenceTime) / (1000 * 60 * 60))
    : 0

  const validationErrors: string[] = []
  if (!Number.isFinite(departureTimestamp)) {
    validationErrors.push('Departure time must be a valid date and time.')
  } else if (departureTimestamp <= referenceTime) {
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
    setReferenceTime(Date.now())
  }

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onSubmit(values)
  }

  return (
    <Card className="border-border bg-card p-6 pb-8 h-full">
      <CardHeader className="space-y-1 px-0 pt-0">
        <CardTitle className="font-display text-xl font-bold uppercase tracking-wide">Charging Constraints</CardTitle>
        <CardDescription>Calibrate your schedule for departure readiness and optimization strategy.</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="space-y-4" onSubmit={submit}>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="batteryCapacity" className="inline-flex items-center gap-2 text-base font-semibold uppercase tracking-wide text-foreground">
                <LightningBoltIcon className="h-4 w-4 text-foreground" />
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
              <Label htmlFor="maxPower" className="inline-flex items-center gap-2 text-base font-semibold uppercase tracking-wide text-foreground">
                <DashboardIcon className="h-4 w-4 text-foreground" />
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
          </div>

          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="currentSoC" className="text-base font-semibold uppercase tracking-wide text-foreground">Current SoC (%)</Label>
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
            
            <div className="space-y-1.5">
              <Label htmlFor="targetSoC" className="text-base font-semibold uppercase tracking-wide text-foreground">Target SoC (%)</Label>
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
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="departureTime" className="text-base font-semibold uppercase tracking-wide text-foreground">Departure Deadline</Label>
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
              <Label htmlFor="priceZone" className="text-base font-semibold uppercase tracking-wide text-foreground">Price Zone</Label>
              <select
                id="priceZone"
                className="flex min-h-11 w-full border border-[#E0DDD5] bg-[#F7F5F0] px-3 py-2 text-base text-foreground shadow-hard-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-foreground font-semibold"
                value={values.priceZone}
                onChange={(event) => updateValue('priceZone', event.target.value as PriceZone)}
              >
                <option value="DK1">DK1</option>
                <option value="DK2">DK2</option>
              </select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="algorithm" className="text-base font-semibold uppercase tracking-wide text-foreground">Optimization Algorithm</Label>
              <select
                id="algorithm"
                className="flex min-h-11 w-full border border-[#E0DDD5] bg-[#F7F5F0] px-3 py-2 text-base text-foreground shadow-hard-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-foreground font-semibold"
                value={values.algorithm}
                onChange={(event) =>
                  updateValue('algorithm', event.target.value as OptimizationAlgorithm)
                }
              >
                <option value="greedy">Greedy</option>
                <option value="optimal">Optimal (DP)</option>
                <option value="mip">MIP</option>
                <option value="naive">Naive</option>
              </select>
            </div>
          </div>

          <div className="space-y-2 mt-4">
            <Label className="inline-flex items-center gap-2 text-base font-semibold uppercase tracking-wide text-foreground">
              <GlobeIcon className="h-4 w-4 text-foreground" />
              Sustainability vs. Price
            </Label>
            <Slider
              min={0}
              max={1}
              step={0.01}
              value={[values.costWeight]}
              onValueChange={(sliderValue) => updateValue('costWeight', sliderValue[0] ?? 0.5)}
            />
            <div className="flex items-center justify-between text-base font-semibold tabular-nums text-muted-foreground uppercase tracking-widest">
              <span>Min CO2</span>
              <span className="text-foreground border border-[#E0DDD5] px-2 py-0.5 shadow-hard-sm">{values.costWeight.toFixed(2)}</span>
              <span>Min Cost</span>
            </div>
          </div>

          <div className="space-y-2 border-t-2 border-foreground mt-4 pt-4 text-base font-sans tabular-nums text-foreground">
            <div className="flex justify-between items-center">
              <span className="font-semibold uppercase tracking-wider text-muted-foreground">Energy needed</span>
              <span className="font-bold text-base">{energyNeeded.toFixed(1)} kWh</span>
            </div>
            <div className="flex justify-between items-center">
              <span className="font-semibold uppercase tracking-wider text-muted-foreground">Duration (Max Pwr)</span>
              <span className="font-bold text-base">{formatHours(estimatedHours)}</span>
            </div>
            <div className="flex justify-between items-center">
              <span className="font-semibold uppercase tracking-wider text-muted-foreground">Time until departure</span>
              <span className="font-bold text-base bg-foreground text-background px-1">{formatHours(availableHours)}</span>
            </div>
          </div>

          {validationErrors.length ? (
            <div className="space-y-1.5 border-l-4 border-[#D95C14] bg-[#EAE6DB] p-3 text-base font-semibold text-foreground">
              {validationErrors.map((error) => (
                <p key={error}>{error}</p>
              ))}
            </div>
          ) : null}

          <div className="mt-auto pt-8 flex flex-col gap-3">
            <Button
              type="submit"
              className="min-h-12 w-full text-base uppercase tracking-widest"
              disabled={isSubmitting || isFormInvalid}
            >
              {isSubmitting ? (
                <span className="inline-flex items-center gap-2">
                  <Spinner className="h-4 w-4 text-primary-foreground" />
                  Generating...
                </span>
              ) : (
                'Generate Plan'
              )}
            </Button>

            <Button
              type="button"
              variant="secondary"
              className="min-h-11 w-full text-base uppercase tracking-widest"
              disabled={isSavingDefaults}
              onClick={() => onSaveDefaults?.(values)}
            >
              {isSavingDefaults ? 'Saving...' : 'Save Defaults'}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}
