import { type FormEvent, type ReactNode, useState } from 'react'
import { CarFront, Check, ChevronLeft, ChevronRight, Clock3, Gauge, MapPin, Save, SlidersHorizontal, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Slider } from '@/components/ui/slider'
import { Spinner } from '@/components/ui/spinner'
import { loadVehicleProfiles, saveVehicleProfiles } from '@/lib/vehicle-profiles'
import type { OptimizationAlgorithm, PriceZone, ScheduleFormValues, VehicleProfile } from '@/types/api'

interface Props {
  onSubmit: (values: ScheduleFormValues) => void
  onSaveDefaults?: (values: ScheduleFormValues) => void
  initialValues?: Partial<ScheduleFormValues>
  isSubmitting: boolean
  isSavingDefaults?: boolean
}

const steps = [
  { title: 'Vehicle', icon: CarFront },
  { title: 'Charging needs', icon: Clock3 },
  { title: 'Preferences', icon: SlidersHorizontal },
]
const batteryPresets = [40, 60, 77, 100]
const powerPresets = [3.7, 7.4, 11, 22]
const algorithmInfo: Record<OptimizationAlgorithm, { label: string; technical: string; description: string }> = {
  greedy: { label: 'Balanced and fast', technical: 'Greedy heuristic', description: 'A quick, dependable plan that balances price and emissions.' },
  optimal: { label: 'Best overall balance', technical: 'Dynamic programming', description: 'Explores more combinations to find the strongest weighted result.' },
  mip: { label: 'Most precise', technical: 'Mixed-integer programming', description: 'Uses a mathematical model for a highly constrained plan.' },
  naive: { label: 'Charge immediately', technical: 'Naive baseline', description: 'Starts charging now and provides a useful comparison baseline.' },
}

const toLocalInput = (date: Date) => new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
const defaultDeparture = () => { const date = new Date(); date.setHours(date.getHours() + 8, 0, 0, 0); return toLocalInput(date) }
const defaults = (initial?: Partial<ScheduleFormValues>): ScheduleFormValues => ({
  batteryCapacity: 77, maxPower: 11, departureTime: defaultDeparture(), targetSoC: 80,
  costWeight: 0.5, currentSoC: 25, priceZone: 'DK2', algorithm: 'greedy', compareStrategies: true,
  ...initial,
})
const formatDuration = (value: number) => !Number.isFinite(value) || value <= 0 ? '0 min' : `${Math.floor(value)} hr ${Math.round(value % 1 * 60)} min`
const selectClass = 'flex min-h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-base font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-1'

export function ScheduleForm({ onSubmit, onSaveDefaults, initialValues, isSubmitting, isSavingDefaults = false }: Props) {
  const [values, setValues] = useState<ScheduleFormValues>(() => defaults(initialValues))
  const [step, setStep] = useState(0)
  const [referenceTime, setReferenceTime] = useState(Date.now)
  const [profiles, setProfiles] = useState<VehicleProfile[]>(loadVehicleProfiles)
  const [profileId, setProfileId] = useState('')
  const [profileName, setProfileName] = useState('')
  const [showProfileSave, setShowProfileSave] = useState(false)

  const update = <K extends keyof ScheduleFormValues>(key: K, value: ScheduleFormValues[K]) => {
    setValues((current) => ({ ...current, [key]: value }))
    setReferenceTime(Date.now())
  }
  const departure = new Date(values.departureTime).getTime()
  const energy = Math.max(0, (values.targetSoC - values.currentSoC) / 100 * values.batteryCapacity)
  const duration = values.maxPower > 0 ? energy / values.maxPower : Infinity
  const available = Number.isFinite(departure) ? Math.max(0, (departure - referenceTime) / 3_600_000) : 0
  const stepErrors = [
    values.batteryCapacity <= 0 || values.maxPower <= 0 ? ['Battery size and charging speed must be above zero.'] : [],
    [
      ...(!Number.isFinite(departure) || departure <= referenceTime ? ['Choose a future departure time.'] : []),
      ...(values.currentSoC > values.targetSoC ? ['The current battery level cannot be above the target.'] : []),
      ...(energy > 0 && duration > available ? ['This target may not be reachable before departure. Try a lower target or faster charger.'] : []),
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
  const submit = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); if (step === 2) onSubmit(values) }

  return (
    <Card className="overflow-hidden">
      <CardHeader className="border-b border-border bg-card/80 pb-4">
        <CardTitle className="text-xl">Create your charging plan</CardTitle>
        <CardDescription>Three quick steps. You can adjust the technical settings if you want to.</CardDescription>
        <ol className="mt-4 grid grid-cols-3 gap-2" aria-label="Charging plan progress">
          {steps.map(({ title, icon: Icon }, index) => (
            <li key={title}>
              <button type="button" className={`w-full rounded-md p-1.5 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary sm:p-2 sm:text-left ${index === step ? 'bg-primary/10 text-foreground' : index < step ? 'text-success' : 'text-muted-foreground'}`} onClick={() => index <= step && setStep(index)} aria-current={index === step ? 'step' : undefined} disabled={index > step}>
                <span className="flex flex-col items-center gap-1 text-[11px] font-semibold leading-tight sm:flex-row sm:gap-2 sm:text-sm"><span className={`grid h-7 w-7 shrink-0 place-items-center rounded-full ${index === step ? 'bg-primary text-primary-foreground' : index < step ? 'bg-success text-success-foreground' : 'bg-muted'}`}>{index < step ? <Check className="h-4 w-4" /> : <Icon className="h-4 w-4" />}</span><span>{title}</span></span>
              </button>
            </li>
          ))}
        </ol>
      </CardHeader>

      <CardContent className="p-5 sm:p-6">
        <form className="space-y-5" onSubmit={submit}>
          {step === 0 ? <>
            <SectionHeading title="Which vehicle are you charging?" description="Select a saved vehicle or enter its battery details." />
            <Field label="Saved vehicle"><div className="flex gap-2"><select value={profileId} onChange={(event) => chooseProfile(event.target.value)} className={selectClass}><option value="">Custom vehicle</option>{profiles.map((profile) => <option key={profile.id} value={profile.id}>{profile.name}</option>)}</select>{profileId ? <Button type="button" variant="ghost" className="px-3" onClick={removeProfile} aria-label="Delete selected vehicle"><Trash2 className="h-4 w-4" /></Button> : null}</div></Field>
            <NumberField id="batteryCapacity" label="Battery size" suffix="kWh" value={values.batteryCapacity} min={1} max={200} step={0.1} onChange={(value) => update('batteryCapacity', value)} />
            <ChoiceChips label="Popular battery sizes" values={batteryPresets} selected={values.batteryCapacity} suffix="kWh" onSelect={(value) => update('batteryCapacity', value)} />
            <NumberField id="maxPower" label="Home charger speed" suffix="kW" value={values.maxPower} min={1} max={50} step={0.1} onChange={(value) => update('maxPower', value)} />
            <ChoiceChips label="Common charger speeds" values={powerPresets} selected={values.maxPower} suffix="kW" onSelect={(value) => update('maxPower', value)} />
            {showProfileSave ? <div className="flex gap-2 rounded-md bg-muted/50 p-3"><Input aria-label="Vehicle profile name" placeholder="Name this vehicle" value={profileName} onChange={(event) => setProfileName(event.target.value)} /><Button type="button" disabled={!profileName.trim()} onClick={addProfile}><Save className="mr-2 h-4 w-4" />Save</Button></div> : <Button type="button" variant="ghost" className="px-0 text-primary" onClick={() => setShowProfileSave(true)}><Save className="mr-2 h-4 w-4" />Save this vehicle for next time</Button>}
          </> : null}

          {step === 1 ? <>
            <SectionHeading title="When should your car be ready?" description="We use the time available to find cheaper and cleaner charging hours." />
            <div className="grid gap-4 sm:grid-cols-2"><NumberField id="currentSoC" label="Battery now" suffix="%" value={values.currentSoC} min={0} max={100} onChange={(value) => update('currentSoC', value)} /><NumberField id="targetSoC" label="Battery target" suffix="%" value={values.targetSoC} min={1} max={100} onChange={(value) => update('targetSoC', value)} /></div>
            <Field label="Departure time" htmlFor="departureTime" hint="Choose when you expect to unplug the car."><Input id="departureTime" type="datetime-local" value={values.departureTime} onChange={(event) => update('departureTime', event.target.value)} required /></Field>
            <Field label="Electricity area" htmlFor="priceZone" hint="DK1 covers western Denmark; DK2 covers eastern Denmark."><div className="relative"><MapPin className="pointer-events-none absolute left-3 top-3.5 h-4 w-4 text-muted-foreground" /><select id="priceZone" value={values.priceZone} onChange={(event) => update('priceZone', event.target.value as PriceZone)} className={`${selectClass} pl-9`}><option value="DK1">DK1 — Western Denmark</option><option value="DK2">DK2 — Eastern Denmark</option></select></div></Field>
            <div className="grid grid-cols-3 gap-2 rounded-lg bg-muted/45 p-4 text-center"><Metric label="Energy needed" value={`${energy.toFixed(1)} kWh`} /><Metric label="Charge time" value={formatDuration(duration)} /><Metric label="Time available" value={formatDuration(available)} /></div>
          </> : null}

          {step === 2 ? <>
            <SectionHeading title="What matters most?" description="Choose a simple preference. We will handle the technical details." />
            <Field label="Price and environmental balance">
              <Slider aria-label="Price and environmental balance" min={0} max={1} step={0.01} value={[values.costWeight]} onValueChange={(value) => update('costWeight', value[0] ?? 0.5)} />
              <div className="flex justify-between text-sm"><span className={values.costWeight < 0.4 ? 'font-semibold text-success' : 'text-muted-foreground'}>Cleaner</span><span className="rounded-full bg-muted px-3 py-1 font-medium">{Math.round(values.costWeight * 100)}% price focus</span><span className={values.costWeight > 0.6 ? 'font-semibold text-primary' : 'text-muted-foreground'}>Cheaper</span></div>
            </Field>
            <details className="group rounded-lg border border-border bg-background p-4">
              <summary className="flex cursor-pointer list-none items-center justify-between font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"><span className="flex items-center gap-2"><Gauge className="h-4 w-4" />Advanced planning settings</span><ChevronRight className="h-4 w-4 transition-transform group-open:rotate-90" /></summary>
              <div className="mt-4 space-y-4 border-t border-border pt-4">
                <Field label="Planning method" htmlFor="algorithm"><select id="algorithm" value={values.algorithm} onChange={(event) => update('algorithm', event.target.value as OptimizationAlgorithm)} className={selectClass}>{Object.entries(algorithmInfo).map(([key, info]) => <option key={key} value={key}>{info.label}</option>)}</select><div className="rounded-md bg-muted/50 p-3 text-sm"><strong>{algorithmInfo[values.algorithm].technical}</strong><span className="mt-1 block text-muted-foreground">{algorithmInfo[values.algorithm].description}</span></div></Field>
                <label className="flex gap-3 rounded-md border border-border p-3 text-sm"><input type="checkbox" className="mt-1 h-4 w-4 accent-primary" checked={values.compareStrategies} onChange={(event) => update('compareStrategies', event.target.checked)} /><span><strong>Compare every planning method</strong><span className="mt-1 block text-muted-foreground">Shows alternatives without adding them to your history.</span></span></label>
              </div>
            </details>
            <div className="rounded-lg border border-success/30 bg-success/10 p-4"><p className="font-semibold text-success">Ready to create your plan</p><p className="mt-1 text-sm text-muted-foreground">Charge {energy.toFixed(1)} kWh before {new Date(values.departureTime).toLocaleString([], { weekday: 'short', hour: '2-digit', minute: '2-digit' })}.</p></div>
          </> : null}

          {stepErrors[step].length ? <div role="alert" className="rounded-md border border-warning/50 bg-warning/15 p-3 text-sm font-medium">{stepErrors[step].map((error) => <p key={error}>{error}</p>)}</div> : null}
          <div className={`${step === 2 ? 'sticky bottom-3 z-20 rounded-lg border border-border bg-card/95 p-3 shadow-lg backdrop-blur lg:static lg:border-0 lg:bg-transparent lg:p-0 lg:shadow-none' : ''} flex gap-3 pt-2`}>
            {step > 0 ? <Button type="button" variant="secondary" onClick={() => setStep((current) => current - 1)}><ChevronLeft className="mr-2 h-4 w-4" />Back</Button> : null}
            {step < 2 ? <Button type="button" className="ml-auto" disabled={stepErrors[step].length > 0} onClick={() => setStep((current) => current + 1)}>Continue<ChevronRight className="ml-2 h-4 w-4" /></Button> : <Button type="submit" className="ml-auto flex-1 whitespace-nowrap sm:flex-none" disabled={isSubmitting}>{isSubmitting ? <span className="inline-flex items-center gap-2"><Spinner className="h-4 w-4" />Building your plan…</span> : <><span className="sm:hidden">Build plan</span><span className="hidden sm:inline">Build my charging plan</span></>}</Button>}
          </div>
          {step === 2 ? <Button type="button" variant="ghost" className="w-full text-muted-foreground" disabled={isSavingDefaults} onClick={() => onSaveDefaults?.(values)}>{isSavingDefaults ? 'Saving…' : 'Use these settings by default'}</Button> : null}
        </form>
      </CardContent>
    </Card>
  )
}

function SectionHeading({ title, description }: { title: string; description: string }) { return <div><h2 className="font-display text-xl font-semibold">{title}</h2><p className="mt-1 text-sm text-muted-foreground">{description}</p></div> }
function Field({ label, htmlFor, hint, children }: { label: string; htmlFor?: string; hint?: string; children: ReactNode }) { return <div className="space-y-1.5"><Label htmlFor={htmlFor} className="text-sm font-semibold">{label}</Label>{children}{hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}</div> }
function NumberField({ id, label, suffix, value, min, max, step = 1, onChange }: { id: string; label: string; suffix: string; value: number; min: number; max: number; step?: number; onChange: (value: number) => void }) { return <Field label={label} htmlFor={id}><div className="relative"><Input id={id} className="pr-14" type="number" value={value} min={min} max={max} step={step} onChange={(event) => onChange(Number(event.target.value))} required /><span className="pointer-events-none absolute right-3 top-3 text-sm text-muted-foreground">{suffix}</span></div></Field> }
function ChoiceChips({ label, values, selected, suffix, onSelect }: { label: string; values: number[]; selected: number; suffix: string; onSelect: (value: number) => void }) { return <div><p className="mb-2 text-xs text-muted-foreground">{label}</p><div className="flex flex-wrap gap-2">{values.map((value) => <button key={value} type="button" className={`min-h-10 rounded-full border px-3 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${selected === value ? 'border-primary bg-primary/10 text-primary' : 'border-border bg-background hover:bg-muted'}`} onClick={() => onSelect(value)}>{value} {suffix}</button>)}</div></div> }
function Metric({ label, value }: { label: string; value: string }) { return <div><p className="text-xs text-muted-foreground">{label}</p><p className="mt-1 text-sm font-semibold">{value}</p></div> }
