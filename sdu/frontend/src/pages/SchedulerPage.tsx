import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { AlertTriangle, CarFront, LogOut, Moon, Sun } from 'lucide-react'
import { ResponsiveDashboard } from '@/components/layout/ResponsiveDashboard'
import { PlanBreakdown } from '@/components/schedule/PlanBreakdown'
import { ScheduleChart } from '@/components/schedule/ScheduleChart'
import { ScheduleForm } from '@/components/schedule/ScheduleForm'
import { ResultsSummary } from '@/components/schedule/ResultsSummary'
import { StatusBanner } from '@/components/schedule/StatusBanner'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { useAuth } from '@/hooks/useAuth'
import { useSchedule } from '@/hooks/useSchedule'
import { useUserConstraints } from '@/hooks/useUserConstraints'
import { cn } from '@/lib/utils'
import { type ScheduleFormValues } from '@/types/api'

type ThemeMode = 'light' | 'dark'
const INTRO_DURATION_MS = 2600

const getInitialTheme = (): ThemeMode => {
  if (typeof window === 'undefined') {
    return 'light'
  }

  const stored = window.localStorage.getItem('theme-mode')
  if (stored === 'light' || stored === 'dark') {
    return stored
  }

  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function SchedulerPage() {
  const { user, logout } = useAuth()
  const scheduleMutation = useSchedule()
  const { query: constraintsQuery, updateMutation: updateConstraintsMutation } = useUserConstraints()

  const [theme, setTheme] = useState<ThemeMode>(getInitialTheme)
  const [showIntro, setShowIntro] = useState<boolean>(true)
  const [dismissedBannerKey, setDismissedBannerKey] = useState<string | null>(null)
  const [lastRequestWindow, setLastRequestWindow] = useState<{
    startTime: string
    endTime: string
  } | null>(null)
  const [lastRequestAlgorithm, setLastRequestAlgorithm] = useState<ScheduleFormValues['algorithm']>('greedy')

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark')
    window.localStorage.setItem('theme-mode', theme)
  }, [theme])

  useEffect(() => {
    if (!showIntro) {
      return
    }

    const timer = window.setTimeout(() => {
      setShowIntro(false)
    }, INTRO_DURATION_MS)

    return () => window.clearTimeout(timer)
  }, [showIntro])

  const formDefaults = useMemo(() => {
    if (!constraintsQuery.data) {
      return undefined
    }

    return {
      batteryCapacity: constraintsQuery.data.defaultBatteryCapacity,
      maxPower: constraintsQuery.data.defaultMaxPower,
      costWeight: constraintsQuery.data.defaultPreferenceWeight,
      priceZone: constraintsQuery.data.priceArea,
    }
  }, [constraintsQuery.data])

  const formKey = useMemo(() => {
    if (!constraintsQuery.data) {
      return 'form-defaults-empty'
    }

    return [
      constraintsQuery.data.defaultBatteryCapacity,
      constraintsQuery.data.defaultMaxPower,
      constraintsQuery.data.defaultPreferenceWeight,
      constraintsQuery.data.priceArea,
    ].join('-')
  }, [constraintsQuery.data])

  const handleSubmit = (values: ScheduleFormValues) => {
    setLastRequestWindow({
      startTime: new Date().toISOString(),
      endTime: values.departureTime,
    })
    setLastRequestAlgorithm(values.algorithm)
    scheduleMutation.mutate(values)
  }

  const handleSaveDefaults = (values: ScheduleFormValues) => {
    updateConstraintsMutation.mutate({
      defaultBatteryCapacity: values.batteryCapacity,
      defaultMaxPower: values.maxPower,
      defaultPreferenceWeight: values.costWeight,
      priceArea: values.priceZone,
    })
  }

  const schedule = scheduleMutation.data ?? null
  const degradedBannerKey = useMemo(() => {
    if (!schedule?.isDegradedMode) {
      return null
    }

    return [
      schedule.fallbackSource,
      schedule.fallbackReason ?? '',
      schedule.fallbackDataAgeHours ?? 'unknown',
    ].join('|')
  }, [schedule])

  useEffect(() => {
    if (!degradedBannerKey) {
      setDismissedBannerKey(null)
      return
    }

    if (dismissedBannerKey && dismissedBannerKey !== degradedBannerKey) {
      setDismissedBannerKey(null)
    }
  }, [degradedBannerKey, dismissedBannerKey])

  const showStatusBanner = Boolean(schedule?.isDegradedMode && degradedBannerKey !== dismissedBannerKey)

  return (
    <div className={cn('min-h-screen pb-6', showIntro && 'intro-active')}>
      {showIntro ? (
        <div className="intro-overlay" aria-hidden="true">
          <div className="intro-overlay__ambient" />
          <div className="intro-overlay__content">
            <p className="intro-overlay__eyebrow">EV Dispatch Initializing</p>
            <div className="intro-road">
              <div className="intro-road__lane" />
              <div className="intro-road__lane intro-road__lane--alt" />
              <div className="intro-road__spark" />
              <CarFront className="intro-road__car" />
            </div>
          </div>
        </div>
      ) : null}
      <StatusBanner
        result={showStatusBanner ? schedule : null}
        onDismiss={() => {
          if (degradedBannerKey) {
            setDismissedBannerKey(degradedBannerKey)
          }
        }}
      />
      <div className="mx-auto max-w-7xl px-4 pb-4 pt-3 sm:px-6 lg:px-8">
        <header className="mb-8 border-b-4 border-foreground pb-6 relative mt-6">
          <div className="absolute top-0 right-0 -mr-2 mt-4 -rotate-[8deg] z-10 pointer-events-none">
            <span className="font-handwriting text-3xl text-[#D95C14] opacity-90 select-none">
              v2.0 Beta
            </span>
          </div>
          <div className="flex flex-wrap items-start justify-between gap-3 relative">
            <div className="max-w-4xl">
              <div className="flex items-center gap-4">
                <span className="inline-block border-2 border-foreground px-2 py-0.5 font-sans text-xs font-bold uppercase tracking-widest bg-foreground text-background shadow-hard-sm">
                  System Active
                </span>
                <p className="font-sans text-xs font-bold uppercase tracking-[0.2em] text-muted-foreground">
                  EV Charging Scheduler
                </p>
              </div>
              <h1 className="mt-4 font-display text-4xl font-bold uppercase tracking-tighter text-foreground sm:text-5xl md:text-6xl">
                Adaptive Energy Routing
              </h1>
              <p className="mt-4 font-sans text-sm font-medium text-foreground max-w-2xl leading-relaxed border-l-2 border-[#D95C14] pl-4">
                Balance spot prices and grid sustainability constraints to formulate an optimized vehicle energy plan before mandatory departure limits.
              </p>
              <p className="mt-4 text-xs font-bold tracking-widest uppercase text-muted-foreground">Operator: {user?.email}</p>
            </div>
            <div className="flex items-center gap-2 self-start flex-col items-end">
              <div className="flex gap-2">
                <Button
                  type="button"
                  variant="outline"
                  className="shadow-hard-sm border-2 border-foreground"
                onClick={() => setTheme((prev) => (prev === 'dark' ? 'light' : 'dark'))}
                aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
                title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
              >
                {theme === 'dark' ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
              </Button>
              <Button type="button" variant="outline" className="min-h-11 shadow-hard-sm border-2 border-foreground uppercase tracking-widest text-xs font-bold" onClick={logout}>
                <LogOut className="mr-2 h-4 w-4" />
                Terminate
              </Button>
              {user?.role === 'ADMIN' ? (
                <Button asChild type="button" variant="outline" className="min-h-11 shadow-hard-sm border-2 border-foreground uppercase tracking-widest text-xs font-bold">
                  <Link to="/admin">Admin tools</Link>
                </Button>
              ) : null}
              </div>
            </div>
          </div>
        </header>

        <ResponsiveDashboard
          constraints={
            <ScheduleForm
              key={formKey}
              onSubmit={handleSubmit}
              onSaveDefaults={handleSaveDefaults}
              initialValues={formDefaults}
              isSubmitting={scheduleMutation.isPending}
              isSavingDefaults={updateConstraintsMutation.isPending}
            />
          }
          content={
            <div className="space-y-3 sm:space-y-4">
              <ResultsSummary result={schedule} isLoading={scheduleMutation.isPending} />
              <ScheduleChart
                slots={schedule?.slots ?? []}
                marketSignals={schedule?.marketSignals ?? []}
                isLoading={scheduleMutation.isPending}
                windowStart={lastRequestWindow?.startTime ?? null}
                windowEnd={lastRequestWindow?.endTime ?? null}
                algorithm={lastRequestAlgorithm}
              />
              <PlanBreakdown result={schedule} isLoading={scheduleMutation.isPending} />

              {scheduleMutation.error ? (
                <Card className="border-warning/50 bg-warning/20">
                  <CardContent className="flex items-start gap-3 p-4">
                    <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-warning-foreground" />
                    <div className="space-y-1 text-sm text-white">
                      <p className="font-semibold">Unable to build a charging schedule</p>
                      <p>{scheduleMutation.error.message}</p>
                      {scheduleMutation.error.status === 400 ? (
                        <p className="font-medium">
                          Validation error: verify your SoC targets, departure time, and zone constraints.
                        </p>
                      ) : null}
                    </div>
                  </CardContent>
                </Card>
              ) : null}

              {updateConstraintsMutation.error ? (
                <p className="text-sm text-destructive">Unable to save your default constraints right now.</p>
              ) : null}

              {updateConstraintsMutation.isSuccess ? (
                <p className="text-sm text-primary">Default constraints saved successfully.</p>
              ) : null}
            </div>
          }
        />
      </div>
    </div>
  )
}

export default SchedulerPage
