import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AlertTriangle, Database, FlaskConical, LogOut } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuth } from '@/hooks/useAuth'
import { ApiError } from '@/services/apiClient'
import { runAdminBenchmark, runAdminDataSync } from '@/services/adminToolsService'
import { type AdminBenchmarkResponse, type PriceZone } from '@/types/api'

function AdminPage() {
  const { user, logout } = useAuth()
  const [scenarios, setScenarios] = useState('300')
  const [seed, setSeed] = useState('20260413')
  const [date, setDate] = useState('today')
  const [zone, setZone] = useState<PriceZone>('DK1')

  const [benchmarkResult, setBenchmarkResult] = useState<AdminBenchmarkResponse | null>(null)
  const [syncResult, setSyncResult] = useState<string | null>(null)
  const [benchmarkError, setBenchmarkError] = useState<string | null>(null)
  const [syncError, setSyncError] = useState<string | null>(null)
  const [benchmarkLoading, setBenchmarkLoading] = useState(false)
  const [syncLoading, setSyncLoading] = useState(false)

  const handleRunBenchmark = async () => {
    setBenchmarkLoading(true)
    setBenchmarkError(null)

    try {
      const response = await runAdminBenchmark({
        scenarios: Number.parseInt(scenarios, 10),
        seed: Number.parseInt(seed, 10),
      })
      setBenchmarkResult(response)
    } catch (caught) {
      if (caught instanceof ApiError) {
        setBenchmarkError(caught.message)
      } else {
        setBenchmarkError('Failed to run benchmark.')
      }
    } finally {
      setBenchmarkLoading(false)
    }
  }

  const handleRunSync = async () => {
    setSyncLoading(true)
    setSyncError(null)

    try {
      const response = await runAdminDataSync(date.trim() || 'today', zone)
      setSyncResult(response)
    } catch (caught) {
      if (caught instanceof ApiError) {
        setSyncError(caught.message)
      } else {
        setSyncError('Failed to sync grid data.')
      }
    } finally {
      setSyncLoading(false)
    }
  }

  return (
    <div className="mx-auto min-h-screen max-w-5xl px-4 py-6 sm:px-6 lg:px-8">
      <header className="mb-6 rounded-lg border border-border/60 bg-card p-5 shadow-soft">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-primary">Admin Control Center</p>
            <h1 className="mt-2 text-2xl font-bold text-foreground">Restricted Operations</h1>
            <p className="mt-1 text-sm text-muted-foreground">
              Signed in as {user?.email}. These controls are hidden from regular users.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Button asChild variant="secondary">
              <Link to="/scheduler">Back to scheduler</Link>
            </Button>
            <Button type="button" variant="secondary" onClick={logout}>
              <LogOut className="mr-2 h-4 w-4" />
              Logout
            </Button>
          </div>
        </div>
      </header>

      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <FlaskConical className="h-4 w-4" />
              Strategy Benchmark Runner
            </CardTitle>
            <CardDescription>Run randomized optimality-gap benchmarks from the UI.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="space-y-1.5">
              <Label htmlFor="benchmark-scenarios">Scenarios</Label>
              <Input
                id="benchmark-scenarios"
                type="number"
                min={1}
                max={2000}
                value={scenarios}
                onChange={(event) => setScenarios(event.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="benchmark-seed">Seed</Label>
              <Input id="benchmark-seed" type="number" value={seed} onChange={(event) => setSeed(event.target.value)} />
            </div>
            <Button type="button" className="w-full" disabled={benchmarkLoading} onClick={handleRunBenchmark}>
              {benchmarkLoading ? 'Running benchmark...' : 'Run benchmark'}
            </Button>
            {benchmarkError ? (
              <p className="text-sm text-destructive">{benchmarkError}</p>
            ) : null}
            {benchmarkResult ? (
              <div className="space-y-1 rounded-md border border-border/60 bg-muted/40 p-3 text-sm">
                <p>
                  Seed: <strong>{benchmarkResult.seed}</strong> | Scenarios: <strong>{benchmarkResult.scenarios}</strong>
                </p>
                <p>Objective gap mean: {benchmarkResult.objectiveGapPercent.mean.toFixed(4)}%</p>
                <p>Cost gap mean: {benchmarkResult.costGapPercent.mean.toFixed(4)}%</p>
                <p>CO2 gap mean: {benchmarkResult.emissionsGapPercent.mean.toFixed(4)}%</p>
                <div className="mt-2 text-xs border-t pt-2 border-border/60">
                  <p className="font-semibold mb-1">Latency Distribution (ms)</p>
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <p className="font-medium">DP (Optimal)</p>
                      <p>Mean: {benchmarkResult.runtimeMs.optimalMeanMs.toFixed(2)}</p>
                      <p>P50: {benchmarkResult.runtimeMs.optimalP50Ms.toFixed(2)}</p>
                      <p>P95: {benchmarkResult.runtimeMs.optimalP95Ms.toFixed(2)}</p>
                    </div>
                    <div>
                      <p className="font-medium">Greedy</p>
                      <p>Mean: {benchmarkResult.runtimeMs.greedyMeanMs.toFixed(2)}</p>
                      <p>P50: {benchmarkResult.runtimeMs.greedyP50Ms.toFixed(2)}</p>
                      <p>P95: {benchmarkResult.runtimeMs.greedyP95Ms.toFixed(2)}</p>
                    </div>
                  </div>
                </div>
              </div>
            ) : null}
            <div className="space-y-2 rounded-md border border-border/60 bg-background p-3 text-xs text-muted-foreground">
              <p className="font-semibold text-foreground">How to read benchmark numbers</p>
              <p>
                Gap metrics are computed as (Greedy - DP) / |DP| * 100, so the sign tells you which strategy is better.
              </p>
              <p>
                Objective gap: positive means DP has lower objective (better), negative means Greedy has lower objective.
              </p>
              <p>
                Cost gap: positive means Greedy is more expensive and DP is cheaper, negative means Greedy is cheaper.
              </p>
              <p>
                CO2 gap: positive means Greedy emits more and DP is cleaner, negative means Greedy emits less.
              </p>
              <p>
                Runtime Distribution: Compare Mean, p50 (Median), and p95 (Tail Latency) between DP and Greedy to evaluate Algorithmic Latency under stress.
              </p>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Database className="h-4 w-4" />
              Grid Data Sync Trigger
            </CardTitle>
            <CardDescription>Manually trigger protected sync endpoint for DK1/DK2.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="space-y-1.5">
              <Label htmlFor="sync-date">Date value</Label>
              <Input
                id="sync-date"
                value={date}
                onChange={(event) => setDate(event.target.value)}
                placeholder="today, tomorrow, or yyyy-mm-dd"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="sync-zone">Zone</Label>
              <select
                id="sync-zone"
                value={zone}
                onChange={(event) => setZone(event.target.value as PriceZone)}
                className="flex min-h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              >
                <option value="DK1">DK1</option>
                <option value="DK2">DK2</option>
              </select>
            </div>
            <Button type="button" className="w-full" disabled={syncLoading} onClick={handleRunSync}>
              {syncLoading ? 'Syncing...' : 'Run data sync'}
            </Button>
            {syncError ? <p className="text-sm text-destructive">{syncError}</p> : null}
            {syncResult ? (
              <div className="rounded-md border border-border/60 bg-muted/40 p-3 text-sm">
                <p>{syncResult}</p>
              </div>
            ) : null}
          </CardContent>
        </Card>
      </div>

      <div className="mt-4 rounded-md border border-warning/60 bg-warning/10 p-3 text-sm text-warning-foreground">
        <p className="flex items-center gap-2 font-medium">
          <AlertTriangle className="h-4 w-4" />
          Admin-only warning
        </p>
        <p className="mt-1">These tools can trigger expensive operations and should not be exposed to regular users.</p>
      </div>
    </div>
  )
}

export default AdminPage
