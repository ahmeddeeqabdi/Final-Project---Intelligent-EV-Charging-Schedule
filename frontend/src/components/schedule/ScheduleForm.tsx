import { type FormEvent, type ReactNode, useState } from 'react'
import { Save, SlidersHorizontal, Trash2 } from 'lucide-react'
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

const toLocalInput = (date: Date) => new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
const defaultDeparture = () => {
  const date = new Date()
  date.setHours(date.getHours() + 8, 0, 0, 0)
  return toLocalInput(date)
}
const defaults = (initial?: Partial<ScheduleFormValues>): ScheduleFormValues => ({
  batteryCapacity: 77, maxPower: 11, departureTime: defaultDeparture(), targetSoC: 80,
  costWeight: 0.5, currentSoC: 25, priceZone: 'DK2', algorithm: 'greedy', compareStrategies: true,
  ...initial,
})
const hours = (value: number) => !Number.isFinite(value) || value <= 0 ? '0h' : `${Math.floor(value)}h ${Math.round(value % 1 * 60)}m`
const selectClass = 'flex min-h-11 w-full border border-border bg-background px-3 py-2 text-sm font-semibold shadow-hard-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-foreground'

export function ScheduleForm({ onSubmit, onSaveDefaults, initialValues, isSubmitting, isSavingDefaults = false }: Props) {
  const [values, setValues] = useState<ScheduleFormValues>(() => defaults(initialValues))
  const [referenceTime, setReferenceTime] = useState(Date.now)
  const [advanced, setAdvanced] = useState(false)
  const [profiles, setProfiles] = useState<VehicleProfile[]>(loadVehicleProfiles)
  const [profileId, setProfileId] = useState('')
  const [profileName, setProfileName] = useState('')
  const update = <K extends keyof ScheduleFormValues>(key: K, value: ScheduleFormValues[K]) => {
    setValues((current) => ({ ...current, [key]: value }))
    setReferenceTime(Date.now())
  }

  const departure = new Date(values.departureTime).getTime()
  const energy = Math.max(0, (values.targetSoC - values.currentSoC) / 100 * values.batteryCapacity)
  const duration = values.maxPower > 0 ? energy / values.maxPower : Infinity
  const available = Number.isFinite(departure) ? Math.max(0, (departure - referenceTime) / 3_600_000) : 0
  const errors: string[] = []
  if (!Number.isFinite(departure) || departure <= referenceTime) errors.push('Choose a future departure time.')
  if (values.currentSoC > values.targetSoC) errors.push('Your current battery level cannot be above the target.')
  if (values.batteryCapacity <= 0 || values.maxPower <= 0) errors.push('Battery size and charging power must be above zero.')
  if (energy > 0 && duration > available) errors.push('The target may not be reachable before departure at this charging speed.')

  const chooseProfile = (id: string) => {
    setProfileId(id)
    const profile = profiles.find((item) => item.id === id)
    if (profile) setValues((current) => ({ ...current, batteryCapacity: profile.batteryCapacity, maxPower: profile.maxPower, priceZone: profile.priceZone }))
  }
  const addProfile = () => {
    if (!profileName.trim()) return
    const profile: VehicleProfile = { id: crypto.randomUUID(), name: profileName.trim(), batteryCapacity: values.batteryCapacity, maxPower: values.maxPower, priceZone: values.priceZone }
    const next = [...profiles, profile]
    setProfiles(next); saveVehicleProfiles(next); setProfileId(profile.id); setProfileName('')
  }
  const removeProfile = () => {
    const next = profiles.filter((profile) => profile.id !== profileId)
    setProfiles(next); saveVehicleProfiles(next); setProfileId('')
  }
  const submit = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); onSubmit(values) }

  return (
    <Card className="h-full border-border bg-card p-6 pb-8">
      <CardHeader className="px-0 pt-0">
        <div className="flex items-start justify-between gap-3">
          <div><CardTitle className="font-display text-xl font-bold uppercase">Plan your charge</CardTitle><CardDescription>Tell us what your car needs and when you are leaving.</CardDescription></div>
          <Button type="button" variant="ghost" className="min-h-9 px-2 text-xs" onClick={() => setAdvanced((value) => !value)}><SlidersHorizontal className="mr-1 h-4 w-4" />{advanced ? 'Simple' : 'Advanced'}</Button>
        </div>
      </CardHeader>
      <CardContent>
        <form className="space-y-4" onSubmit={submit}>
          <Field label="Saved vehicle"><select value={profileId} onChange={(event) => chooseProfile(event.target.value)} className={selectClass}><option value="">Custom vehicle</option>{profiles.map((profile) => <option key={profile.id} value={profile.id}>{profile.name}</option>)}</select></Field>
          {advanced ? <div className="flex gap-2"><Input aria-label="Vehicle profile name" placeholder="Profile name" value={profileName} onChange={(event) => setProfileName(event.target.value)} /><Button type="button" variant="secondary" className="px-3" disabled={!profileName.trim()} onClick={addProfile} title="Save vehicle"><Save className="h-4 w-4" /></Button><Button type="button" variant="ghost" className="px-3" disabled={!profileId} onClick={removeProfile} title="Delete vehicle"><Trash2 className="h-4 w-4" /></Button></div> : null}

          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2"><NumberField id="batteryCapacity" label="Battery size (kWh)" value={values.batteryCapacity} min={1} max={200} step={0.1} onChange={(value) => update('batteryCapacity', value)} />{advanced ? <NumberField id="maxPower" label="Max power (kW)" value={values.maxPower} min={1} max={50} step={0.1} onChange={(value) => update('maxPower', value)} /> : null}</div>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2"><NumberField id="currentSoC" label="Current battery (%)" value={values.currentSoC} min={0} max={100} onChange={(value) => update('currentSoC', value)} /><NumberField id="targetSoC" label="Target battery (%)" value={values.targetSoC} min={1} max={100} onChange={(value) => update('targetSoC', value)} /></div>
          <Field label="Ready by" htmlFor="departureTime"><Input id="departureTime" type="datetime-local" value={values.departureTime} onChange={(event) => update('departureTime', event.target.value)} required /></Field>
          <Field label="Electricity area" htmlFor="priceZone"><select id="priceZone" value={values.priceZone} onChange={(event) => update('priceZone', event.target.value as PriceZone)} className={selectClass}><option value="DK1">DK1 — Western Denmark</option><option value="DK2">DK2 — Eastern Denmark</option></select></Field>

          {advanced ? <>
            <Field label="Planning method" htmlFor="algorithm"><select id="algorithm" value={values.algorithm} onChange={(event) => update('algorithm', event.target.value as OptimizationAlgorithm)} className={selectClass}><option value="greedy">Fast heuristic</option><option value="optimal">Dynamic planning</option><option value="mip">Mathematical model</option><option value="naive">Charge now</option></select></Field>
            <Field label="CO2 versus price"><Slider min={0} max={1} step={0.01} value={[values.costWeight]} onValueChange={(value) => update('costWeight', value[0] ?? 0.5)} /><div className="flex justify-between text-xs text-muted-foreground"><span>Cleaner</span><span>{Math.round(values.costWeight * 100)}% price focus</span><span>Cheaper</span></div></Field>
            <label className="flex gap-3 border border-border bg-background p-3 text-sm"><input type="checkbox" className="mt-1" checked={values.compareStrategies} onChange={(event) => update('compareStrategies', event.target.checked)} /><span><strong>Compare all strategies</strong><span className="block text-muted-foreground">See cost and CO2 from all four planning methods.</span></span></label>
          </> : null}

          <div className="space-y-1 border-t-2 border-foreground pt-3 text-sm"><p className="flex justify-between"><span>Energy needed</span><strong>{energy.toFixed(1)} kWh</strong></p><p className="flex justify-between"><span>Estimated charge time</span><strong>{hours(duration)}</strong></p><p className="flex justify-between"><span>Time available</span><strong>{hours(available)}</strong></p></div>
          {errors.length ? <div className="border-l-4 border-[#D95C14] bg-muted p-3 text-sm font-semibold">{errors.map((error) => <p key={error}>{error}</p>)}</div> : null}
          <div className="flex flex-col gap-3 pt-4"><Button type="submit" className="min-h-12 w-full uppercase tracking-wider" disabled={isSubmitting || errors.length > 0}>{isSubmitting ? <span className="inline-flex items-center gap-2"><Spinner className="h-4 w-4" />Building your plan…</span> : 'Build my charging plan'}</Button><Button type="button" variant="secondary" className="w-full" disabled={isSavingDefaults} onClick={() => onSaveDefaults?.(values)}>{isSavingDefaults ? 'Saving…' : 'Save as my defaults'}</Button></div>
        </form>
      </CardContent>
    </Card>
  )
}

function Field({ label, htmlFor, children }: { label: string; htmlFor?: string; children: ReactNode }) {
  return <div className="space-y-1.5"><Label htmlFor={htmlFor} className="font-semibold">{label}</Label>{children}</div>
}
function NumberField({ id, label, value, min, max, step = 1, onChange }: { id: string; label: string; value: number; min: number; max: number; step?: number; onChange: (value: number) => void }) {
  return <Field label={label} htmlFor={id}><Input id={id} type="number" value={value} min={min} max={max} step={step} onChange={(event) => onChange(Number(event.target.value))} required /></Field>
}
