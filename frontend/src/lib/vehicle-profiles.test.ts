import { beforeEach, describe, expect, it } from 'vitest'

import type { VehicleProfile } from '@/types/api'
import { loadVehicleProfiles, saveVehicleProfiles } from './vehicle-profiles'

const profile: VehicleProfile = {
  id: 'family-ev',
  name: 'Family EV',
  batteryCapacity: 77,
  maxPower: 11,
  priceZone: 'DK2',
}

describe('vehicle profiles', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it('persists and restores profiles', () => {
    saveVehicleProfiles([profile])

    expect(loadVehicleProfiles()).toEqual([profile])
  })

  it('returns an empty collection for malformed storage', () => {
    window.localStorage.setItem('ev-charging-vehicle-profiles', '{not-json')

    expect(loadVehicleProfiles()).toEqual([])
  })
})
