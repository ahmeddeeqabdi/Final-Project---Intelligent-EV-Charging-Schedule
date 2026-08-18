import { lazy, Suspense, useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { AlertTriangle, BarChart3, CarFront, ChevronRight, List, LogOut, Moon, Sun } from 'lucide-react'
import { ResponsiveDashboard } from '@/components/layout/ResponsiveDashboard'
import { PlanBreakdown } from '@/components/schedule/PlanBreakdown'
import { ScheduleActions } from '@/components/schedule/ScheduleActions'
import { ScheduleForm } from '@/components/schedule/ScheduleForm'
import { ScheduleHistory } from '@/components/schedule/ScheduleHistory'
import { ScheduleLoadingState } from '@/components/schedule/ScheduleLoadingState'
import { ResultsSummary } from '@/components/schedule/ResultsSummary'
import { StatusBanner } from '@/components/schedule/StatusBanner'
import { StrategyComparison } from '@/components/schedule/StrategyComparison'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { useAuth } from '@/hooks/useAuth'
import { useSchedule } from '@/hooks/useSchedule'
import { useUserConstraints } from '@/hooks/useUserConstraints'
import { cn } from '@/lib/utils'
import { type OptimizationAlgorithm, type ScheduleFormValues } from '@/types/api'

type ThemeMode = 'light' | 'dark'
const ScheduleChart = lazy(() => import('@/components/schedule/ScheduleChart').then((module) => ({ default: module.ScheduleChart })))
const INTRO_DURATION_MS = 1400
const INTRO_STORAGE_KEY = 'ev-scheduler-intro-seen'

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
  const [showIntro, setShowIntro] = useState<boolean>(() => window.localStorage.getItem(INTRO_STORAGE_KEY) !== 'true')
  const [dismissedBannerKey, setDismissedBannerKey] = useState<string | null>(null)
  const [lastRequestWindow, setLastRequestWindow] = useState<{
    startTime: string
    endTime: string
  } | null>(null)
  const [lastValues, setLastValues] = useState<ScheduleFormValues | null>(null)
  const [activeAlgorithm, setActiveAlgorithm] = useState<OptimizationAlgorithm>('greedy')
  const resultsRef = useRef<HTMLDivElement>(null)


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
      window.localStorage.setItem(INTRO_STORAGE_KEY, 'true')
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
    setLastValues(values)
    setActiveAlgorithm(values.algorithm)
    scheduleMutation.mutate(values, {
      onSuccess: () => window.setTimeout(() => {
        resultsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
        resultsRef.current?.focus({ preventScroll: true })
      }, 80),
    })
  }

  const handleSaveDefaults = (values: ScheduleFormValues) => {
    updateConstraintsMutation.mutate({
      defaultBatteryCapacity: values.batteryCapacity,
      defaultMaxPower: values.maxPower,
      defaultPreferenceWeight: values.costWeight,
      priceArea: values.priceZone,
    })
  }

  const schedule = scheduleMutation.data?.comparisons[activeAlgorithm] ?? scheduleMutation.data?.selected ?? null
  const baseline = scheduleMutation.data?.comparisons.naive ?? null
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

  const showStatusBanner = Boolean(schedule?.isDegradedMode && degradedBannerKey !== dismissedBannerKey)

  return (
    <div className={cn('min-h-screen pb-4', showIntro && 'intro-active')}>
      {showIntro ? (
        <div className="intro-overlay" role="dialog" aria-label="Welcome to the EV charging scheduler">
          <div className="intro-overlay__ambient" />
          <div className="intro-overlay__content">
            <p className="intro-overlay__eyebrow">EV Dispatch Initializing</p>
            <div className="intro-road">
              <div className="intro-road__lane" />
              <div className="intro-road__lane intro-road__lane--alt" />
              <div className="intro-road__spark" />
              <CarFront className="intro-road__car" />
            </div>
            <Button type="button" variant="secondary" className="intro-overlay__skip" onClick={() => { setShowIntro(false); window.localStorage.setItem(INTRO_STORAGE_KEY, 'true') }}>Skip intro</Button>
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
      <div className="mx-auto max-w-screen-2xl px-4 pb-8 pt-2 sm:px-6 lg:px-10">
        <header className="relative mb-6 mt-3 border-b border-border pb-5">
          <div className="flex flex-wrap items-start justify-between gap-3 relative">
            <div className="max-w-4xl">
              <p className="font-semibold text-primary">EV Charging Scheduler</p>
              <h1 className="mt-1 font-display text-3xl font-bold tracking-tight text-foreground sm:text-4xl lg:text-5xl">
                Plan your next charge
              </h1>
              <p className="mt-2 text-sm text-muted-foreground">Save money, reduce emissions, and leave with the battery level you need.</p>
            </div>
            <div className="flex items-center gap-2 self-start flex-col items-end">
              <div className="flex gap-2">
                <Button
                  type="button"
                  variant="secondary"
                  className="px-3"
                onClick={() => setTheme((prev) => (prev === 'dark' ? 'light' : 'dark'))}
                aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
                title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
              >
                {theme === 'dark' ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
              </Button>
              <Button type="button" variant="secondary" onClick={logout}>
                <LogOut className="mr-2 h-4 w-4" />
                Sign out
              </Button>
              {user?.role === 'ADMIN' ? (
                <Button asChild type="button" variant="secondary">
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
            <div ref={resultsRef} tabIndex={-1} className="space-y-4 outline-none" aria-label="Charging plan results">
              {scheduleMutation.isPending ? <ScheduleLoadingState /> : <ResultsSummary result={schedule} baseline={baseline} currentSoC={lastValues?.currentSoC} targetSoC={lastValues?.targetSoC} batteryCapacity={lastValues?.batteryCapacity} isLoading={false} />}
              {scheduleMutation.data && lastValues ? <StrategyComparison comparisons={scheduleMutation.data.comparisons} active={activeAlgorithm} costWeight={lastValues.costWeight} onSelect={setActiveAlgorithm} /> : null}
              <ScheduleActions result={schedule} />
              {schedule ? <div className="grid gap-3">
                <details className="group rounded-lg border border-border bg-card shadow-sm">
                  <summary className="flex min-h-14 cursor-pointer list-none items-center justify-between p-4 font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"><span className="flex items-center gap-2"><BarChart3 className="h-5 w-5 text-primary" />Explore price, CO2, and power chart</span><ChevronRight className="h-5 w-5 transition-transform group-open:rotate-90" /></summary>
                  <div className="border-t border-border p-4"><Suspense fallback={<ScheduleLoadingState />}><ScheduleChart slots={schedule.slots} marketSignals={schedule.marketSignals} isLoading={false} windowStart={lastRequestWindow?.startTime ?? null} windowEnd={lastRequestWindow?.endTime ?? null} /></Suspense></div>
                </details>
                <details className="group rounded-lg border border-border bg-card shadow-sm">
                  <summary className="flex min-h-14 cursor-pointer list-none items-center justify-between p-4 font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"><span className="flex items-center gap-2"><List className="h-5 w-5 text-primary" />View the hour-by-hour breakdown</span><ChevronRight className="h-5 w-5 transition-transform group-open:rotate-90" /></summary>
                  <div className="border-t border-border p-4"><PlanBreakdown result={schedule} isLoading={false} /></div>
                </details>
              </div> : null}
              <ScheduleHistory />

              {scheduleMutation.error ? (
                <Card className="border-warning/50 bg-warning/20">
                  <CardContent className="flex items-start gap-3 p-4">
                    <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-warning-foreground" />
                    <div className="space-y-1 text-sm text-foreground">
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
