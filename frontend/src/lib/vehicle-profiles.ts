import type { VehicleProfile } from '@/types/api'

const STORAGE_KEY = 'ev-charging-vehicle-profiles'

export const loadVehicleProfiles = (): VehicleProfile[] => {
  if (typeof window === 'undefined') return []
  try {
    const value = JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? '[]')
    return Array.isArray(value) ? value : []
  } catch {
    return []
  }
}

export const saveVehicleProfiles = (profiles: VehicleProfile[]) => {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(profiles))
}
