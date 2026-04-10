package com.sdu.evcharging.dto.auth;

public record UpdateUserConstraintsRequest(
        double defaultBatteryCapacity,
        double defaultMaxPower,
        double defaultPreferenceWeight
) {
}
