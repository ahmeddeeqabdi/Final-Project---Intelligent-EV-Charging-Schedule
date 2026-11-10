import { lazy, Suspense, type ReactNode, useEffect, useMemo, useRef, useState } from 'react'
import { AlertTriangle, BarChart3, CarFront, ChevronRight, List } from 'lucide-react'
import { AppHeader } from '@/components/layout/AppHeader'
import { ResponsiveDashboard } from '@/components/layout/ResponsiveDashboard'
import { ChargingTimeline } from '@/components/schedule/ChargingTimeline'
import { PlanBreakdown } from '@/components/schedule/PlanBreakdown'
import { PlanEmptyState } from '@/components/schedule/PlanEmptyState'
import { ScheduleActions } from '@/components/schedule/ScheduleActions'
import { ScheduleForm, type SaveDefaultsStatus } from '@/components/schedule/ScheduleForm'
import { ScheduleHistory } from '@/components/schedule/ScheduleHistory'
import { ScheduleLoadingState } from '@/components/schedule/ScheduleLoadingState'
import { ResultsSummary } from '@/components/schedule/ResultsSummary'
import { StatusBanner } from '@/components/schedule/StatusBanner'
import { StrategyComparison } from '@/components/schedule/StrategyComparison'
import { Button } from '@/components/ui/button'
import { useSchedule } from '@/hooks/useSchedule'
import { useUserConstraints } from '@/hooks/useUserConstraints'
import { cn } from '@/lib/utils'
import { type OptimizationAlgorithm, type ScheduleFormValues } from '@/types/api'

const ScheduleChart = lazy(() => import('@/components/schedule/ScheduleChart').then((module) => ({ default: module.ScheduleChart })))
const INTRO_DURATION_MS = 1400
const INTRO_STORAGE_KEY = 'ev-scheduler-intro-seen'
const SAVED_NOTICE_MS = 4000

const readIntroSeen = () => {
  try {
    return window.localStorage.getItem(INTRO_STORAGE_KEY) === 'true'
  } catch {
    return true
  }
}
const markIntroSeen = () => {
  try {
    window.localStorage.setItem(INTRO_STORAGE_KEY, 'true')
  } catch {
    // Without storage the intro simply plays again next visit.
  }
}

function SchedulerPage() {
  const scheduleMutation = useSchedule()
  const { query: constraintsQuery, updateMutation: updateConstraintsMutation } = useUserConstraints()

  const [showIntro, setShowIntro] = useState<boolean>(() => !readIntroSeen())
  const [dismissedBannerKey, setDismissedBannerKey] = useState<string | null>(null)
  const [lastRequestWindow, setLastRequestWindow] = useState<{ startTime: string; endTime: string } | null>(null)
  const [lastValues, setLastValues] = useState<ScheduleFormValues | null>(null)
  const [activeAlgorithm, setActiveAlgorithm] = useState<OptimizationAlgorithm>('greedy')
  const resultsRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!showIntro) return
    const timer = window.setTimeout(() => {
      setShowIntro(false)
      markIntroSeen()
    }, INTRO_DURATION_MS)
    return () => window.clearTimeout(timer)
  }, [showIntro])

  const { isSuccess: defaultsSaved, reset: resetDefaultsMutation } = updateConstraintsMutation
  useEffect(() => {
    if (!defaultsSaved) return
    const timer = window.setTimeout(resetDefaultsMutation, SAVED_NOTICE_MS)
    return () => window.clearTimeout(timer)
  }, [defaultsSaved, resetDefaultsMutation])

  const formDefaults = useMemo(() => {
    if (!constraintsQuery.data) return undefined
    return {
      batteryCapacity: constraintsQuery.data.defaultBatteryCapacity,
      maxPower: constraintsQuery.data.defaultMaxPower,
      costWeight: constraintsQuery.data.defaultPreferenceWeight,
      priceZone: constraintsQuery.data.priceArea,
    }
  }, [constraintsQuery.data])

  const formKey = useMemo(() => {
    if (!constraintsQuery.data) return 'form-defaults-empty'
    return [
      constraintsQuery.data.defaultBatteryCapacity,
      constraintsQuery.data.defaultMaxPower,
      constraintsQuery.data.defaultPreferenceWeight,
      constraintsQuery.data.priceArea,
    ].join('-')
  }, [constraintsQuery.data])

  const handleSubmit = (values: ScheduleFormValues) => {
    setLastRequestWindow({ startTime: new Date().toISOString(), endTime: values.departureTime })
    setLastValues(values)
    setActiveAlgorithm(values.algorithm)
    scheduleMutation.mutate(values, {
      onSuccess: () => window.setTimeout(() => {
        // Only scroll when the results are below the fold (stacked mobile layout).
        const top = resultsRef.current?.getBoundingClientRect().top ?? 0
        if (top > window.innerHeight * 0.6) {
          const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
          resultsRef.current?.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' })
        }
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

  const saveDefaultsStatus: SaveDefaultsStatus = updateConstraintsMutation.isPending
    ? 'saving'
    : updateConstraintsMutation.isError
      ? 'error'
      : updateConstraintsMutation.isSuccess
        ? 'saved'
        : 'idle'

  const schedule = scheduleMutation.data?.comparisons[activeAlgorithm] ?? scheduleMutation.data?.selected ?? null
  const baseline = scheduleMutation.data?.comparisons.naive ?? null
  const degradedBannerKey = useMemo(() => {
    if (!schedule?.isDegradedMode) return null
    return [schedule.fallbackSource, schedule.fallbackReason ?? '', schedule.fallbackDataAgeHours ?? 'unknown'].join('|')
  }, [schedule])
  const showStatusBanner = Boolean(schedule?.isDegradedMode && degradedBannerKey !== dismissedBannerKey)

  return (
    <div className={cn('min-h-screen', showIntro && 'intro-active')}>
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
            <Button type="button" variant="secondary" className="intro-overlay__skip" onClick={() => { setShowIntro(false); markIntroSeen() }}>Skip intro</Button>
          </div>
        </div>
      ) : null}
      <AppHeader />

      <div className="mx-auto max-w-screen-2xl px-4 pb-12 pt-6 sm:px-6 lg:px-10 lg:pt-8">
        <div className="mb-6 lg:mb-8">
          <h1 className="font-display text-3xl font-bold tracking-tight text-foreground sm:text-4xl">Plan your next charge</h1>
          <p className="mt-2 max-w-2xl text-muted-foreground">Save money, cut emissions, and still leave with the battery level you need.</p>
        </div>

        <ResponsiveDashboard
          constraints={
            <ScheduleForm
              key={formKey}
              onSubmit={handleSubmit}
              onSaveDefaults={handleSaveDefaults}
              initialValues={formDefaults}
              isSubmitting={scheduleMutation.isPending}
              saveDefaultsStatus={saveDefaultsStatus}
            />
          }
          content={
            <div ref={resultsRef} tabIndex={-1} className="scroll-mt-24 space-y-4 outline-none" aria-label="Charging plan results">
              {scheduleMutation.error ? (
                <div role="alert" className="flex items-start gap-3 rounded-lg border border-destructive/40 bg-destructive/10 p-4">
                  <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-destructive" />
                  <div className="space-y-1 text-sm text-foreground">
                    <p className="font-semibold">We couldn’t build a charging plan</p>
                    <p>{scheduleMutation.error.message}</p>
                    {scheduleMutation.error.status === 400 ? (
                      <p className="text-muted-foreground">Check your battery levels, departure time, and electricity area, then try again.</p>
                    ) : null}
                  </div>
                </div>
              ) : null}

              <StatusBanner
                result={showStatusBanner ? schedule : null}
                onDismiss={() => {
                  if (degradedBannerKey) setDismissedBannerKey(degradedBannerKey)
                }}
              />

              {scheduleMutation.isPending ? (
                <ScheduleLoadingState />
              ) : schedule ? (
                <>
                  <ResultsSummary
                    result={schedule}
                    baseline={baseline}
                    currentSoC={lastValues?.currentSoC}
                    targetSoC={lastValues?.targetSoC}
                    batteryCapacity={lastValues?.batteryCapacity}
                  />
                  <ChargingTimeline result={schedule} windowStart={lastRequestWindow?.startTime} windowEnd={lastRequestWindow?.endTime} />
                  {scheduleMutation.data && lastValues ? (
                    <StrategyComparison comparisons={scheduleMutation.data.comparisons} active={activeAlgorithm} costWeight={lastValues.costWeight} onSelect={setActiveAlgorithm} />
                  ) : null}
                  <ScheduleActions result={schedule} />
                  <div className="grid gap-3">
                    <Disclosure icon={<BarChart3 className="h-5 w-5 text-primary" />} title="Detailed price, CO₂, and power chart">
                      <Suspense fallback={<ScheduleLoadingState />}>
                        <ScheduleChart slots={schedule.slots} marketSignals={schedule.marketSignals} windowStart={lastRequestWindow?.startTime ?? null} windowEnd={lastRequestWindow?.endTime ?? null} />
                      </Suspense>
                    </Disclosure>
                    <Disclosure icon={<List className="h-5 w-5 text-primary" />} title="Hour-by-hour breakdown">
                      <PlanBreakdown result={schedule} />
                    </Disclosure>
                  </div>
                </>
              ) : (
                <PlanEmptyState />
              )}

              <ScheduleHistory />
            </div>
          }
        />
      </div>
    </div>
  )
}

function Disclosure({ icon, title, children }: { icon: ReactNode; title: string; children: ReactNode }) {
  return (
    <details className="group rounded-lg border border-border bg-card shadow-sm">
      <summary className="flex min-h-14 cursor-pointer list-none items-center justify-between rounded-lg p-4 font-semibold hover:bg-secondary/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary">
        <span className="flex items-center gap-2">{icon}{title}</span>
        <ChevronRight className="h-5 w-5 transition-transform group-open:rotate-90" />
      </summary>
      <div className="border-t border-border p-4">{children}</div>
    </details>
  )
}

export default SchedulerPage
