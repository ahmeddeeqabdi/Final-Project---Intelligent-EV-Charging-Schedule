import { useState } from 'react'
import { AlertTriangle, Database, FlaskConical } from 'lucide-react'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { FormError } from '@/components/auth/AuthLayout'
import { AppHeader } from '@/components/layout/AppHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuth } from '@/hooks/useAuth'
import { ApiError } from '@/services/apiClient'
import { runAdminBenchmark, runAdminDataSync } from '@/services/adminToolsService'
import { type AdminBenchmarkResponse, type PriceZone } from '@/types/api'

type StrategyKey = 'optimal' | 'greedy' | 'mip' | 'naive'
type Metric = 'objective' | 'cost' | 'emissions' | 'runtimeMs'

const strategyLabel: Record<StrategyKey, string> = { optimal: 'DP (Optimal)', greedy: 'Greedy', mip: 'MIP', naive: 'Naive' }
const strategies = Object.keys(strategyLabel) as StrategyKey[]
const gapMetrics: Array<[Metric, string]> = [['objective', 'Objective gap mean'], ['cost', 'Cost gap mean'], ['emissions', 'CO₂ gap mean']]
const selectClass = 'flex min-h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground'

/** (Baseline − Target) / |Baseline| × 100: positive means the target strategy is better. */
const gapPercent = (baseline: number, target: number) => ((baseline - target) / Math.max(Math.abs(baseline), 1e-9)) * 100

/** Tracks loading, error, and result state for one admin action. */
function useAction<T>(fallbackError: string) {
  const [state, setState] = useState<{ loading: boolean; error: string | null; result: T | null }>({ loading: false, error: null, result: null })
  const run = async (action: () => Promise<T>) => {
    setState((current) => ({ ...current, loading: true, error: null }))
    try {
      const result = await action()
      setState({ loading: false, error: null, result })
    } catch (caught) {
      setState((current) => ({ ...current, loading: false, error: caught instanceof ApiError ? caught.message : fallbackError }))
    }
  }
  return { ...state, run }
}

function BenchmarkChart({ result, metric, title }: { result: AdminBenchmarkResponse; metric: Metric; title: string }) {
  const data = strategies.map((strategy) => ({ name: strategyLabel[strategy], value: result[strategy][metric].mean }))
  return (
    <div className="h-52 rounded-md border border-border bg-background p-2">
      <p className="mb-2 text-xs font-bold">{title}</p>
      <ResponsiveContainer width="100%" height="85%">
        <BarChart data={data} margin={{ left: 4, right: 4 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
          <XAxis dataKey="name" tick={{ fontSize: 10, fill: 'hsl(var(--muted-foreground))' }} />
          <YAxis tick={{ fontSize: 10, fill: 'hsl(var(--muted-foreground))' }} />
          <Tooltip contentStyle={{ background: 'hsl(var(--card))', border: '1px solid hsl(var(--border))' }} />
          <Bar dataKey="value" fill="hsl(var(--primary))" radius={[2, 2, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

function StrategySelect({ label, value, onChange }: { label: string; value: StrategyKey; onChange: (value: StrategyKey) => void }) {
  return (
    <label className="flex flex-1 flex-col gap-1 text-xs font-semibold">
      {label}
      <select className={selectClass} value={value} onChange={(event) => onChange(event.target.value as StrategyKey)}>
        {strategies.map((strategy) => <option key={strategy} value={strategy}>{strategyLabel[strategy]}</option>)}
      </select>
    </label>
  )
}

function AdminPage() {
  const { user } = useAuth()
  const [scenarios, setScenarios] = useState('300')
  const [seed, setSeed] = useState('20260413')
  const [date, setDate] = useState('today')
  const [zone, setZone] = useState<PriceZone>('DK1')
  const [baseline, setBaseline] = useState<StrategyKey>('naive')
  const [target, setTarget] = useState<StrategyKey>('optimal')
  const benchmark = useAction<AdminBenchmarkResponse>('Failed to run benchmark.')
  const sync = useAction<string>('Failed to sync grid data.')
  const result = benchmark.result

  return (
    <div className="min-h-screen">
      <AppHeader />
      <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
        <div className="mb-6">
          <p className="text-sm font-semibold text-primary">Admin tools</p>
          <h1 className="mt-1 font-display text-3xl font-bold tracking-tight">Restricted operations</h1>
          <p className="mt-2 text-sm text-muted-foreground">Signed in as {user?.email}. These controls are only visible to administrators.</p>
        </div>

        <div className="grid items-start gap-4 md:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2"><FlaskConical className="h-4 w-4 text-primary" />Strategy benchmark</CardTitle>
              <CardDescription>Run randomized optimality-gap benchmarks across all strategies.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5"><Label htmlFor="benchmark-scenarios">Scenarios</Label><Input id="benchmark-scenarios" type="number" min={1} max={2000} value={scenarios} onChange={(event) => setScenarios(event.target.value)} /></div>
                <div className="space-y-1.5"><Label htmlFor="benchmark-seed">Seed</Label><Input id="benchmark-seed" type="number" value={seed} onChange={(event) => setSeed(event.target.value)} /></div>
              </div>
              <Button type="button" className="w-full" disabled={benchmark.loading} onClick={() => benchmark.run(() => runAdminBenchmark({ scenarios: Number.parseInt(scenarios, 10), seed: Number.parseInt(seed, 10) }))}>
                {benchmark.loading ? 'Running benchmark…' : 'Run benchmark'}
              </Button>
              <FormError message={benchmark.error} />
              {result ? (
                <div className="space-y-3 rounded-md border border-border bg-muted/40 p-3 text-sm">
                  <p>Seed <strong>{result.seed}</strong> · <strong>{result.scenarios}</strong> scenarios</p>
                  <div className="flex gap-3">
                    <StrategySelect label="Baseline" value={baseline} onChange={setBaseline} />
                    <StrategySelect label="Target" value={target} onChange={setTarget} />
                  </div>
                  <div className="space-y-1 rounded-md border border-border bg-background p-3 tabular-nums">
                    <p className="mb-2 border-b border-border pb-1 text-xs font-semibold">{strategyLabel[baseline]} vs {strategyLabel[target]}</p>
                    {gapMetrics.map(([metric, label]) => (
                      <p key={metric} className="flex justify-between"><span>{label}</span><span>{gapPercent(result[baseline][metric].mean, result[target][metric].mean).toFixed(4)}%</span></p>
                    ))}
                  </div>
                  <div>
                    <p className="mb-2 text-xs font-semibold">Latency (ms)</p>
                    <div className="grid grid-cols-2 gap-2 text-xs tabular-nums">
                      {strategies.map((strategy) => {
                        const { mean, p50, p95 } = result[strategy].runtimeMs
                        return (
                          <div key={strategy}>
                            <p className="font-medium">{strategyLabel[strategy]}</p>
                            <p className="text-muted-foreground">mean {mean.toFixed(2)} · p50 {p50.toFixed(2)} · p95 {p95.toFixed(2)}</p>
                          </div>
                        )
                      })}
                    </div>
                  </div>
                  <BenchmarkChart result={result} metric="cost" title="Average cost" />
                  <BenchmarkChart result={result} metric="emissions" title="Average CO₂ emissions" />
                  <BenchmarkChart result={result} metric="runtimeMs" title="Average runtime (ms)" />
                </div>
              ) : null}
              <div className="space-y-1.5 rounded-md border border-border bg-background p-3 text-xs text-muted-foreground">
                <p className="font-semibold text-foreground">How to read the gaps</p>
                <p>Each gap is (Baseline − Target) / |Baseline| × 100.</p>
                <p>A positive value means the target strategy has the lower objective, cost, or CO₂ — that is, it performs better than the baseline.</p>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2"><Database className="h-4 w-4 text-primary" />Grid data sync</CardTitle>
              <CardDescription>Manually fetch market data for DK1 or DK2.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="space-y-1.5"><Label htmlFor="sync-date">Date</Label><Input id="sync-date" value={date} onChange={(event) => setDate(event.target.value)} placeholder="today, tomorrow, or yyyy-mm-dd" /></div>
              <div className="space-y-1.5">
                <Label htmlFor="sync-zone">Zone</Label>
                <select id="sync-zone" value={zone} onChange={(event) => setZone(event.target.value as PriceZone)} className={selectClass}>
                  <option value="DK1">DK1 — Western Denmark</option>
                  <option value="DK2">DK2 — Eastern Denmark</option>
                </select>
              </div>
              <Button type="button" className="w-full" disabled={sync.loading} onClick={() => sync.run(() => runAdminDataSync(date.trim() || 'today', zone))}>
                {sync.loading ? 'Syncing…' : 'Run data sync'}
              </Button>
              <FormError message={sync.error} />
              {sync.result ? <p role="status" className="rounded-md border border-border bg-muted/40 p-3 text-sm">{sync.result}</p> : null}
            </CardContent>
          </Card>
        </div>

        <div className="mt-4 flex gap-2 rounded-md border border-warning/60 bg-warning/10 p-3 text-sm">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-warning-foreground dark:text-warning" />
          <p><span className="font-semibold">Admin only.</span> These tools can trigger expensive operations and should not be exposed to regular users.</p>
        </div>
      </div>
    </div>
  )
}

export default AdminPage
