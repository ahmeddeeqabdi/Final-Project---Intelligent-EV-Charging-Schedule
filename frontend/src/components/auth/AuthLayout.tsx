import { type ReactNode, useState } from 'react'
import { AlertCircle, Eye, EyeOff, Leaf, LineChart, PiggyBank } from 'lucide-react'
import { BrandMark } from '@/components/layout/BrandMark'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useTheme } from '@/hooks/useTheme'

const benefits = [
  { icon: PiggyBank, title: 'Pay less per kWh', body: 'Shift charging into the cheapest hours of the Danish day-ahead spot market.' },
  { icon: Leaf, title: 'Charge on cleaner power', body: 'Weigh grid CO₂ intensity for DK1 and DK2 alongside price.' },
  { icon: LineChart, title: 'See the reasoning', body: 'Compare four planning methods and inspect every charging hour.' },
]

export function AuthLayout({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  // Keeps the stored theme applied on public pages too.
  useTheme()

  return (
    <div className="grid min-h-screen lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
      <aside className="relative hidden overflow-hidden bg-foreground p-10 text-background lg:flex lg:flex-col xl:p-14">
        <div className="pointer-events-none absolute -right-24 -top-24 h-96 w-96 rounded-full bg-primary/30 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-32 left-10 h-80 w-80 rounded-full bg-primary/20 blur-3xl" />
        <div className="relative [&_span]:text-background [&_.text-muted-foreground]:text-background/70">
          <BrandMark />
        </div>
        <div className="relative mt-auto max-w-lg">
          <h2 className="font-display text-4xl font-bold leading-tight tracking-tight">Charge when electricity is cheapest and cleanest.</h2>
          <ul className="mt-10 space-y-6">
            {benefits.map(({ icon: Icon, title, body }) => (
              <li key={title} className="flex gap-4">
                <span className="grid h-10 w-10 shrink-0 place-items-center rounded-md bg-background/10 text-primary"><Icon className="h-5 w-5" /></span>
                <span>
                  <span className="block font-semibold">{title}</span>
                  <span className="mt-0.5 block text-sm text-background/70">{body}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      </aside>

      <main className="flex items-center justify-center px-4 py-10 sm:px-8">
        <div className="w-full max-w-md">
          <BrandMark className="mb-10 lg:hidden" />
          <h1 className="font-display text-3xl font-bold tracking-tight">{title}</h1>
          <p className="mt-2 text-muted-foreground">{description}</p>
          <div className="mt-8">{children}</div>
        </div>
      </main>
    </div>
  )
}

export function FormError({ message }: { message: string | null }) {
  if (!message) return null
  return (
    <div role="alert" className="flex items-start gap-2 rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-foreground">
      <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-destructive" />
      <p>{message}</p>
    </div>
  )
}

interface PasswordFieldProps {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  autoComplete: string
  hint?: string
  invalid?: boolean
}

export function PasswordField({ id, label, value, onChange, autoComplete, hint, invalid }: PasswordFieldProps) {
  const [visible, setVisible] = useState(false)
  const hintId = hint ? `${id}-hint` : undefined
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      <div className="relative">
        <Input
          id={id}
          type={visible ? 'text' : 'password'}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          autoComplete={autoComplete}
          aria-describedby={hintId}
          aria-invalid={invalid || undefined}
          className="pr-12"
          required
        />
        <button
          type="button"
          onClick={() => setVisible((current) => !current)}
          className="absolute inset-y-0 right-0 grid w-11 place-items-center rounded-r-md text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          aria-label={visible ? 'Hide password' : 'Show password'}
          aria-pressed={visible}
        >
          {visible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
        </button>
      </div>
      {hint ? <p id={hintId} className="text-xs text-muted-foreground">{hint}</p> : null}
    </div>
  )
}
