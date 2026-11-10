import { CalendarClock, Gauge, PlugZap } from 'lucide-react'

const steps = [
  { icon: Gauge, title: 'Describe your car', body: 'Battery size, charger speed, and how full the battery is today.' },
  { icon: CalendarClock, title: 'Set a departure time', body: 'The longer the car is plugged in, the more flexibility there is to save.' },
  { icon: PlugZap, title: 'Get your plan', body: 'We pick the cheapest, cleanest hours from live Danish market data.' },
]

/** Shown before the first plan: explains what the dashboard will do instead of leaving a blank area. */
export function PlanEmptyState() {
  return (
    <div className="overflow-hidden rounded-lg border border-border bg-card shadow-sm">
      <div className="p-5 sm:p-6">
        <p className="text-sm font-semibold text-primary">How it works</p>
        <h2 className="mt-1 font-display text-2xl font-bold tracking-tight">Charge when power is cheap and clean</h2>
        <p className="mt-2 max-w-2xl text-sm text-muted-foreground">
          Electricity prices and grid CO₂ change every hour. Instead of charging the moment you plug in,
          the scheduler shifts charging into the best hours before you leave.
        </p>
      </div>
      <IllustrativeDay />
      <ol className="grid gap-px border-t border-border bg-border sm:grid-cols-3">
        {steps.map(({ icon: Icon, title, body }, index) => (
          <li key={title} className="bg-card p-5">
            <span className="flex items-center gap-2 text-sm font-semibold">
              <span className="grid h-7 w-7 place-items-center rounded-full bg-primary/10 text-primary"><Icon className="h-4 w-4" /></span>
              <span className="text-muted-foreground">{index + 1}.</span> {title}
            </span>
            <p className="mt-2 text-sm text-muted-foreground">{body}</p>
          </li>
        ))}
      </ol>
    </div>
  )
}

// A typical winter day profile (relative prices), purely illustrative.
const dayShape = [0.32, 0.28, 0.25, 0.24, 0.25, 0.3, 0.45, 0.66, 0.76, 0.69, 0.6, 0.55, 0.52, 0.5, 0.54, 0.62, 0.8, 0.96, 1, 0.92, 0.76, 0.6, 0.47, 0.38]
const startHour = 16

function IllustrativeDay() {
  const hours = Array.from({ length: 16 }, (_, index) => (startHour + index) % 24)
  return (
    <div className="px-5 pb-5 sm:px-6" aria-hidden="true">
      <div className="rounded-md border border-dashed border-border bg-background/60 p-4">
        <div className="flex h-24 items-end gap-1">
          {hours.map((hour) => {
            const cheap = hour >= 0 && hour <= 4
            return (
              <div key={hour} className="flex h-full flex-1 flex-col justify-end">
                <div className={cheap ? 'rounded-t-[3px] bg-primary' : 'rounded-t-[3px] bg-muted-foreground/25'} style={{ height: `${15 + dayShape[hour] * 80}%` }} />
              </div>
            )
          })}
        </div>
        <div className="mt-2 flex justify-between text-[11px] text-muted-foreground">
          <span>Plug in 16:00</span>
          <span className="font-medium text-primary">Charge overnight</span>
          <span>Leave 08:00</span>
        </div>
      </div>
    </div>
  )
}
