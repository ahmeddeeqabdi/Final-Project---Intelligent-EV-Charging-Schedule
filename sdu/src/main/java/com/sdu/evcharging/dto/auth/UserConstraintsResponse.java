package com.sdu.evcharging.dto.auth;

public record UserConstraintsResponse(
        double defaultBatteryCapacity,
        double defaultMaxPower,
        double defaultPreferenceWeight
) {
}
