import { type FormEvent, type ReactNode, type Ref, useEffect, useRef, useState } from 'react'
import { AlertTriangle, CarFront, Check, CheckCircle2, ChevronLeft, ChevronRight, Clock3, Gauge, MapPin, Save, SlidersHorizontal, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Slider } from '@/components/ui/slider'
import { Spinner } from '@/components/ui/spinner'
import { formatDayTime, formatDuration, formatKwh, strategyInfo } from '@/lib/format'
import { cn } from '@/lib/utils'
import { loadVehicleProfiles, saveVehicleProfiles } from '@/lib/vehicle-profiles'
import type { OptimizationAlgorithm, PriceZone, ScheduleFormValues, VehicleProfile } from '@/types/api'

export type SaveDefaultsStatus = 'idle' | 'saving' | 'saved' | 'error'

interface Props {
  onSubmit: (values: ScheduleFormValues) => void
  onSaveDefaults?: (values: ScheduleFormValues) => void
  initialValues?: Partial<ScheduleFormValues>
  isSubmitting: boolean
  saveDefaultsStatus?: SaveDefaultsStatus
}

const steps = [
  { title: 'Vehicle', icon: CarFront },
  { title: 'Charging needs', icon: Clock3 },
  { title: 'Preferences', icon: SlidersHorizontal },
]
const batteryPresets = [40, 60, 77, 100]
const powerPresets = [3.7, 7.4, 11, 22]

const toLocalInput = (date: Date) => new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
const defaultDeparture = () => { const date = new Date(); date.setHours(date.getHours() + 8, 0, 0, 0); return toLocalInput(date) }
const departurePresets = () => {
  const inFour = new Date(); inFour.setHours(inFour.getHours() + 4, 0, 0, 0)
  const tomorrow = (hour: number) => { const date = new Date(); date.setDate(date.getDate() + 1); date.setHours(hour, 0, 0, 0); return date }
  return [
    { label: 'In 4 hours', value: toLocalInput(inFour) },
    { label: 'Tomorrow 07:00', value: toLocalInput(tomorrow(7)) },
    { label: 'Tomorrow 08:00', value: toLocalInput(tomorrow(8)) },
  ]
}
const defaults = (initial?: Partial<ScheduleFormValues>): ScheduleFormValues => ({
  batteryCapacity: 77, maxPower: 11, departureTime: defaultDeparture(), targetSoC: 80,
  costWeight: 0.5, currentSoC: 25, priceZone: 'DK2', algorithm: 'greedy', compareStrategies: true,
  ...initial,
})
const isPercent = (value: number) => Number.isFinite(value) && value >= 0 && value <= 100
const selectClass = 'flex min-h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-base font-medium text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-1'

export function ScheduleForm({ onSubmit, onSaveDefaults, initialValues, isSubmitting, saveDefaultsStatus = 'idle' }: Props) {
  const [values, setValues] = useState<ScheduleFormValues>(() => defaults(initialValues))
  const [step, setStep] = useState(0)
  const [referenceTime, setReferenceTime] = useState(Date.now)
  const [profiles, setProfiles] = useState<VehicleProfile[]>(loadVehicleProfiles)
  const [profileId, setProfileId] = useState('')
  const [profileName, setProfileName] = useState('')
  const [showProfileSave, setShowProfileSave] = useState(false)
  const headingRef = useRef<HTMLHeadingElement>(null)
  const hasNavigated = useRef(false)

  useEffect(() => {
    // Move focus to the new step's heading so keyboard and screen-reader users follow along.
    if (hasNavigated.current) headingRef.current?.focus()
  }, [step])

  const goTo = (next: number) => { hasNavigated.current = true; setStep(next) }
  const update = <K extends keyof ScheduleFormValues>(key: K, value: ScheduleFormValues[K]) => {
    setValues((current) => ({ ...current, [key]: value }))
    setReferenceTime(Date.now())
  }
  const departure = new Date(values.departureTime).getTime()
  const energy = Math.max(0, (values.targetSoC - values.currentSoC) / 100 * values.batteryCapacity)
  const duration = values.maxPower > 0 ? energy / values.maxPower : Infinity
  const available = Number.isFinite(departure) ? Math.max(0, (departure - referenceTime) / 3_600_000) : 0
  const flexibility = available > 0 && Number.isFinite(duration) ? duration / available : 1
  const stepErrors = [
    !(values.batteryCapacity > 0) || !(values.maxPower > 0) ? ['Battery size and charging speed must be above zero.'] : [],
    [
      ...(!isPercent(values.currentSoC) || !isPercent(values.targetSoC) ? ['Battery levels must be between 0% and 100%.'] : []),
      ...(!Number.isFinite(departure) || departure <= referenceTime ? ['Choose a future departure time.'] : []),
      ...(values.currentSoC > values.targetSoC ? ['The current battery level cannot be above the target.'] : []),
      ...(energy > 0 && duration > available && departure > referenceTime ? ['This target may not be reachable before departure. Try a lower target or a faster charger.'] : []),
    ],
    [],
  ]

  const chooseProfile = (id: string) => {
    setProfileId(id)
    const profile = profiles.find((item) => item.id === id)
    if (profile) setValues((current) => ({ ...current, batteryCapacity: profile.batteryCapacity, maxPower: profile.maxPower, priceZone: profile.priceZone }))
  }
  const addProfile = () => {
    if (!profileName.trim()) return
    const profile: VehicleProfile = { id: crypto.randomUUID(), name: profileName.trim(), batteryCapacity: values.batteryCapacity, maxPower: values.maxPower, priceZone: values.priceZone }
    const next = [...profiles, profile]
    setProfiles(next); saveVehicleProfiles(next); setProfileId(profile.id); setProfileName(''); setShowProfileSave(false)
  }
  const removeProfile = () => {
    const next = profiles.filter((profile) => profile.id !== profileId)
    setProfiles(next); saveVehicleProfiles(next); setProfileId('')
  }
  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (step < 2) {
      if (!stepErrors[step].length) goTo(step + 1)
      return
    }
    onSubmit(values)
  }

  return (
    <Card className="overflow-hidden">
      <CardHeader className="gap-3 space-y-0 border-b border-border pb-4">
        <div className="flex items-baseline justify-between gap-3">
          <h2 className="font-display text-lg font-semibold">Create a charging plan</h2>
          <span className="text-xs font-medium text-muted-foreground">Step {step + 1} of {steps.length}</span>
        </div>
        <ol className="grid grid-cols-3 gap-2" aria-label="Charging plan progress">
          {steps.map(({ title, icon: Icon }, index) => {
            const state = index === step ? 'current' : index < step ? 'done' : 'todo'
            return (
              <li key={title}>
                <button
                  type="button"
                  className="group w-full rounded-md text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 ring-offset-card disabled:cursor-default"
                  onClick={() => index < step && goTo(index)}
                  aria-current={state === 'current' ? 'step' : undefined}
                  aria-label={`${title}${state === 'done' ? ' (completed)' : ''}`}
                  disabled={index > step}
                >
                  <span className={cn('block h-1.5 rounded-full transition-colors', state === 'todo' ? 'bg-muted' : state === 'done' ? 'bg-success' : 'bg-primary')} />
                  <span className={cn('mt-2 flex items-center gap-1.5 whitespace-nowrap text-xs font-semibold', state === 'current' ? 'text-foreground' : state === 'done' ? 'text-success group-hover:underline' : 'text-muted-foreground')}>
                    {state === 'done' ? <Check className="h-3.5 w-3.5 shrink-0" /> : <Icon className="hidden h-3.5 w-3.5 shrink-0 min-[400px]:block" />}
                    {title}
                  </span>
                </button>
              </li>
            )
          })}
        </ol>
      </CardHeader>

      <CardContent className="p-5 sm:p-6">
        <form className="space-y-5" onSubmit={submit} noValidate>
          <div key={step} className="animate-rise space-y-5">
            {step === 0 ? <>
              <SectionHeading headingRef={headingRef} title="Which vehicle are you charging?" description="Pick a saved vehicle or enter its battery details." />
              {profiles.length ? (
                <Field label="Saved vehicle" htmlFor="savedVehicle">
                  <div className="flex gap-2">
                    <select id="savedVehicle" value={profileId} onChange={(event) => chooseProfile(event.target.value)} className={selectClass}>
                      <option value="">Custom vehicle</option>
                      {profiles.map((profile) => <option key={profile.id} value={profile.id}>{profile.name}</option>)}
                    </select>
                    {profileId ? <Button type="button" variant="ghost" className="w-11 shrink-0 px-0" onClick={removeProfile} aria-label="Delete selected vehicle"><Trash2 className="h-4 w-4" /></Button> : null}
                  </div>
                </Field>
              ) : null}
              <NumberField id="batteryCapacity" label="Battery size" suffix="kWh" value={values.batteryCapacity} min={1} max={200} step={0.1} onChange={(value) => update('batteryCapacity', value)}>
                <ChoiceChips label="Popular battery sizes" values={batteryPresets} selected={values.batteryCapacity} suffix="kWh" onSelect={(value) => update('batteryCapacity', value)} />
              </NumberField>
              <NumberField id="maxPower" label="Charger speed" suffix="kW" value={values.maxPower} min={1} max={50} step={0.1} onChange={(value) => update('maxPower', value)}>
                <ChoiceChips label="Common charger speeds" values={powerPresets} selected={values.maxPower} suffix="kW" onSelect={(value) => update('maxPower', value)} />
              </NumberField>
              {showProfileSave ? (
                <div className="flex gap-2 rounded-md bg-muted/50 p-3">
                  <Input aria-label="Vehicle profile name" placeholder="e.g. Family car" value={profileName} autoFocus onChange={(event) => setProfileName(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); addProfile() } }} />
                  <Button type="button" disabled={!profileName.trim()} onClick={addProfile}><Save className="mr-2 h-4 w-4" />Save</Button>
                </div>
              ) : (
                <Button type="button" variant="ghost" className="-ml-2 min-h-10 px-2 text-primary" onClick={() => setShowProfileSave(true)}><Save className="mr-2 h-4 w-4" />Save this vehicle for next time</Button>
              )}
            </> : null}

            {step === 1 ? <>
              <SectionHeading headingRef={headingRef} title="When should your car be ready?" description="More time plugged in means more chances to find cheap, clean hours." />
              <div className="space-y-3">
                <div className="grid grid-cols-2 gap-3">
                  <NumberField id="currentSoC" label="Battery now" suffix="%" value={values.currentSoC} min={0} max={100} onChange={(value) => update('currentSoC', value)} />
                  <NumberField id="targetSoC" label="Battery target" suffix="%" value={values.targetSoC} min={1} max={100} onChange={(value) => update('targetSoC', value)} />
                </div>
                <BatteryBar current={values.currentSoC} target={values.targetSoC} />
              </div>
              <Field label="Departure time" htmlFor="departureTime">
                <Input id="departureTime" type="datetime-local" value={values.departureTime} onChange={(event) => update('departureTime', event.target.value)} required />
                <div className="flex flex-wrap gap-2 pt-1">
                  {departurePresets().map((preset) => (
                    <Chip key={preset.label} selected={values.departureTime === preset.value} onClick={() => update('departureTime', preset.value)}>{preset.label}</Chip>
                  ))}
                </div>
              </Field>
              <Field label="Electricity area" htmlFor="priceZone" hint="DK1 is western Denmark (Jutland, Funen); DK2 is eastern Denmark (Zealand, Copenhagen).">
                <div className="relative">
                  <MapPin className="pointer-events-none absolute left-3 top-3.5 h-4 w-4 text-muted-foreground" />
                  <select id="priceZone" value={values.priceZone} onChange={(event) => update('priceZone', event.target.value as PriceZone)} className={`${selectClass} pl-9`}>
                    <option value="DK1">DK1 — Western Denmark</option>
                    <option value="DK2">DK2 — Eastern Denmark</option>
                  </select>
                </div>
              </Field>
              <FeasibilityCard energy={energy} duration={duration} available={available} flexibility={flexibility} />
            </> : null}

            {step === 2 ? <>
              <SectionHeading headingRef={headingRef} title="What matters most to you?" description="Slide toward what you care about. We handle the technical details." />
              <Field label="Price and environmental balance">
                <Slider aria-label="Price and environmental balance" aria-valuetext={`${Math.round(values.costWeight * 100)}% price focus`} min={0} max={1} step={0.05} value={[values.costWeight]} onValueChange={(value) => update('costWeight', value[0] ?? 0.5)} />
                <div className="flex items-center justify-between text-sm">
                  <button type="button" className={cn('rounded px-1 font-medium', values.costWeight < 0.4 ? 'text-success' : 'text-muted-foreground hover:text-foreground')} onClick={() => update('costWeight', 0.2)}>Cleaner</button>
                  <span className="rounded-full bg-muted px-3 py-1 text-xs font-semibold">{balanceLabel(values.costWeight)}</span>
                  <button type="button" className={cn('rounded px-1 font-medium', values.costWeight > 0.6 ? 'text-primary' : 'text-muted-foreground hover:text-foreground')} onClick={() => update('costWeight', 0.8)}>Cheaper</button>
                </div>
              </Field>
              <details className="group rounded-lg border border-border bg-background">
                <summary className="flex min-h-12 cursor-pointer list-none items-center justify-between rounded-lg px-4 font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary">
                  <span className="flex items-center gap-2"><Gauge className="h-4 w-4 text-muted-foreground" />Advanced settings</span>
                  <ChevronRight className="h-4 w-4 transition-transform group-open:rotate-90" />
                </summary>
                <div className="space-y-4 border-t border-border p-4">
                  <Field label="Planning method" htmlFor="algorithm">
                    <select id="algorithm" value={values.algorithm} onChange={(event) => update('algorithm', event.target.value as OptimizationAlgorithm)} className={selectClass}>
                      {Object.entries(strategyInfo).map(([key, info]) => <option key={key} value={key}>{info.label}</option>)}
                    </select>
                    <p className="text-xs text-muted-foreground"><strong className="text-foreground">{strategyInfo[values.algorithm].technical}.</strong> {strategyInfo[values.algorithm].description}</p>
                  </Field>
                  <label className="flex cursor-pointer gap-3 rounded-md border border-border p-3 text-sm hover:bg-secondary/50">
                    <input type="checkbox" className="mt-0.5 h-4 w-4 accent-primary" checked={values.compareStrategies} onChange={(event) => update('compareStrategies', event.target.checked)} />
                    <span><strong>Compare every planning method</strong><span className="mt-0.5 block text-muted-foreground">Shows alternatives side by side without adding them to your history.</span></span>
                  </label>
                </div>
              </details>
              <div className="rounded-lg border border-border bg-secondary/60 p-4 text-sm">
                <p className="font-semibold">Summary</p>
                <p className="mt-1 text-muted-foreground">
                  Add <span className="font-medium text-foreground">{formatKwh(energy)}</span> ({values.currentSoC}% → {values.targetSoC}%) before{' '}
                  <span className="font-medium text-foreground">{Number.isFinite(departure) ? formatDayTime(departure) : '—'}</span> in {values.priceZone}.
                </p>
              </div>
            </> : null}
          </div>

          {stepErrors[step].length ? (
            <div role="alert" className="flex gap-2 rounded-md border border-warning/60 bg-warning/15 p-3 text-sm font-medium text-foreground">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-warning-foreground dark:text-warning" />
              <div>{stepErrors[step].map((error) => <p key={error}>{error}</p>)}</div>
            </div>
          ) : null}

          <div className={cn('flex gap-3', step === 2 && 'sticky bottom-3 z-20 rounded-lg border border-border bg-card/95 p-3 shadow-lg backdrop-blur lg:static lg:border-0 lg:bg-transparent lg:p-0 lg:shadow-none')}>
            {step > 0 ? <Button type="button" variant="secondary" onClick={() => goTo(step - 1)}><ChevronLeft className="mr-1 h-4 w-4" />Back</Button> : null}
            {step < 2 ? (
              <Button type="submit" className="ml-auto min-w-32" disabled={stepErrors[step].length > 0}>Continue<ChevronRight className="ml-1 h-4 w-4" /></Button>
            ) : (
              <Button type="submit" size="lg" className="ml-auto flex-1 whitespace-nowrap" disabled={isSubmitting}>
                {isSubmitting ? <span className="inline-flex items-center gap-2"><Spinner className="h-4 w-4 text-primary-foreground" />Building your plan…</span> : 'Build my charging plan'}
              </Button>
            )}
          </div>
          {step === 2 && onSaveDefaults ? (
            <div className="flex flex-col items-center gap-1">
              <Button type="button" variant="ghost" className="min-h-10 text-muted-foreground" disabled={saveDefaultsStatus === 'saving'} onClick={() => onSaveDefaults(values)}>
                {saveDefaultsStatus === 'saving' ? 'Saving…' : 'Use these settings by default'}
              </Button>
              <p role="status" className="min-h-4 text-xs">
                {saveDefaultsStatus === 'saved' ? <span className="inline-flex items-center gap-1 text-success"><CheckCircle2 className="h-3.5 w-3.5" />Saved as your defaults</span> : null}
                {saveDefaultsStatus === 'error' ? <span className="text-destructive">Couldn’t save your defaults. Please try again.</span> : null}
              </p>
            </div>
          ) : null}
        </form>
      </CardContent>
    </Card>
  )
}

const balanceLabel = (weight: number) => {
  if (weight <= 0.15) return 'Lowest CO₂'
  if (weight < 0.4) return 'Mostly cleaner'
  if (weight <= 0.6) return 'Balanced'
  if (weight < 0.85) return 'Mostly cheaper'
  return 'Lowest price'
}

function SectionHeading({ title, description, headingRef }: { title: string; description: string; headingRef: Ref<HTMLHeadingElement> }) {
  return (
    <div>
      <h3 ref={headingRef} tabIndex={-1} className="font-display text-xl font-semibold outline-none">{title}</h3>
      <p className="mt-1 text-sm text-muted-foreground">{description}</p>
    </div>
  )
}

function Field({ label, htmlFor, hint, children }: { label: string; htmlFor?: string; hint?: string; children: ReactNode }) {
  return (
    <div className="space-y-2">
      <Label htmlFor={htmlFor} className="text-sm font-semibold">{label}</Label>
      {children}
      {hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
    </div>
  )
}

function NumberField({ id, label, suffix, value, min, max, step = 1, onChange, children }: { id: string; label: string; suffix: string; value: number; min: number; max: number; step?: number; onChange: (value: number) => void; children?: ReactNode }) {
  return (
    <Field label={label} htmlFor={id}>
      <div className="relative">
        <Input id={id} className="pr-14 tabular-nums" type="number" inputMode="decimal" value={Number.isNaN(value) ? '' : value} min={min} max={max} step={step} onChange={(event) => onChange(event.target.value === '' ? Number.NaN : Number(event.target.value))} required />
        <span className="pointer-events-none absolute right-3 top-3 text-sm text-muted-foreground">{suffix}</span>
      </div>
      {children}
    </Field>
  )
}

function Chip({ selected, onClick, children }: { selected: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      className={cn(
        'min-h-9 rounded-full border px-3 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary',
        selected ? 'border-primary bg-primary/10 text-primary' : 'border-border bg-background text-foreground hover:bg-muted',
      )}
      onClick={onClick}
    >
      {children}
    </button>
  )
}

function ChoiceChips({ label, values, selected, suffix, onSelect }: { label: string; values: number[]; selected: number; suffix: string; onSelect: (value: number) => void }) {
  return (
    <div role="group" aria-label={label} className="flex flex-wrap gap-2">
      {values.map((value) => <Chip key={value} selected={selected === value} onClick={() => onSelect(value)}>{value} {suffix}</Chip>)}
    </div>
  )
}

function BatteryBar({ current, target }: { current: number; target: number }) {
  const clamp = (value: number) => Math.min(100, Math.max(0, Number.isFinite(value) ? value : 0))
  const now = clamp(current)
  const goal = clamp(target)
  return (
    <div aria-hidden="true">
      <div className="relative h-3 overflow-hidden rounded-full bg-muted">
        <div className="absolute inset-y-0 left-0 bg-foreground/45" style={{ width: `${now}%` }} />
        {goal > now ? (
          <div className="absolute inset-y-0 bg-primary [background-image:repeating-linear-gradient(-45deg,transparent_0_4px,hsl(var(--primary-foreground)/0.25)_4px_8px)]" style={{ left: `${now}%`, width: `${goal - now}%` }} />
        ) : null}
      </div>
      <div className="mt-1.5 flex justify-between text-xs text-muted-foreground">
        <span className="inline-flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-foreground/45" />Now {now}%</span>
        <span className="inline-flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-primary" />To add {Math.max(0, goal - now)}%</span>
      </div>
    </div>
  )
}

function FeasibilityCard({ energy, duration, available, flexibility }: { energy: number; duration: number; available: number; flexibility: number }) {
  const tone = flexibility > 1 ? 'bad' : flexibility > 0.75 ? 'tight' : 'good'
  const message = energy <= 0
    ? 'No charging needed for this target.'
    : tone === 'bad'
      ? 'Not enough time to reach the target.'
      : tone === 'tight'
        ? 'Tight window — little room to shift charging.'
        : `Plenty of flexibility: charging only needs ${Math.round(flexibility * 100)}% of the time available.`
  return (
    <div className="rounded-lg border border-border bg-secondary/60 p-4">
      <dl className="grid grid-cols-3 gap-2 text-center">
        <Metric label="Energy needed" value={formatKwh(energy)} />
        <Metric label="Charge time" value={formatDuration(duration)} />
        <Metric label="Time available" value={formatDuration(available)} />
      </dl>
      <div className="mt-3 h-2 overflow-hidden rounded-full bg-muted" aria-hidden="true">
        <div
          className={cn('h-full rounded-full transition-all', tone === 'bad' ? 'bg-destructive' : tone === 'tight' ? 'bg-warning' : 'bg-success')}
          style={{ width: `${Math.min(100, Math.max(energy > 0 ? 4 : 0, flexibility * 100))}%` }}
        />
      </div>
      <p className={cn('mt-2 text-xs font-medium', tone === 'bad' ? 'text-destructive' : tone === 'good' ? 'text-success' : 'text-foreground')}>{message}</p>
    </div>
  )
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-1 text-sm font-semibold tabular-nums">{value}</dd>
    </div>
  )
}
