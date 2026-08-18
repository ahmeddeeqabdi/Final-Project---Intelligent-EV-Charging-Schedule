export type OptimizationAlgorithm = 'naive' | 'greedy' | 'optimal' | 'mip'

export type PriceZone = 'DK1' | 'DK2'

export interface ScheduleRequest {
  batteryCapacity: number
  maxPower: number
  departureTime: string
  targetSoC: number
  costWeight: number
}

export interface ScheduledSlot {
  timestamp: string
  powerValue: number
  energyPrice: number
  co2Intensity: number
}

export interface MarketSignalPoint {
  timestamp: string
  energyPrice: number | null
  co2Intensity: number | null
}

export interface ScheduleResult {
  totalCost: number
  totalCO2: number
  isDegradedMode: boolean
  fallbackSource: string
  fallbackReason?: string
  fallbackDataAgeHours?: number
  slots: ScheduledSlot[]
  marketSignals: MarketSignalPoint[]
}

export interface ScheduleFormValues extends ScheduleRequest {
  currentSoC: number
  priceZone: PriceZone
  algorithm: OptimizationAlgorithm
}

export interface BackendScheduleRequest {
  currentSocPercent: number
  targetSocPercent: number
  batteryCapacityKwh: number
  maxChargingPowerKw: number
  plugInTime: string
  departureTime: string
  priceZone: PriceZone
  weightPrice: number
  weightCO2: number
}

export interface BackendChargingSlot {
  timestamp: string
  powerDraw: number
  currentPrice: number
  currentCO2: number
}

export interface BackendMarketSignalPoint {
  timestamp: string
  energyPrice: number | null
  co2Intensity: number | null
}

export interface BackendDegradedMode {
  enabled: boolean
  reason: string | null
  source: string
  dataAgeHours: number | null
}

export interface BackendScheduleResult {
  slots: BackendChargingSlot[]
  totalPredictedCost: number
  totalPredictedEmissions: number
  degradedMode: BackendDegradedMode
  marketSignals?: BackendMarketSignalPoint[]
}

export interface BackendErrorResponse {
  message: string
  error: string
  status: number
}

export interface AuthUser {
  id: number
  email: string
  role: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface SignupRequest {
  email: string
  password: string
}

export interface AuthResponse {
  token: string
  user: AuthUser
}

export interface AuthContextValue {
  token: string | null
  user: AuthUser | null
  isAuthenticated: boolean
  login: (payload: LoginRequest) => Promise<void>
  signup: (payload: SignupRequest) => Promise<void>
  logout: () => void
}

export interface UserConstraints {
  defaultBatteryCapacity: number
  defaultMaxPower: number
  defaultPreferenceWeight: number
  priceArea: PriceZone
}

export interface UpdateUserConstraintsRequest {
  defaultBatteryCapacity: number
  defaultMaxPower: number
  defaultPreferenceWeight: number
  priceArea: PriceZone
}

export interface AdminBenchmarkMetricSummary {
  mean: number
  p50: number
  p95: number
  max: number
}

export interface StrategyMetrics {
  objective: AdminBenchmarkMetricSummary
  cost: AdminBenchmarkMetricSummary
  emissions: AdminBenchmarkMetricSummary
  runtimeMs: AdminBenchmarkMetricSummary
}

export interface AdminBenchmarkResponse {
  scenarios: number
  seed: number
  optimal: StrategyMetrics
  greedy: StrategyMetrics
  mip: StrategyMetrics
  naive: StrategyMetrics
}
