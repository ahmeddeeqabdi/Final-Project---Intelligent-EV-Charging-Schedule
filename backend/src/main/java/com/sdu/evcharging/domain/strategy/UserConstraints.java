package com.sdu.evcharging.domain.strategy;

import java.time.LocalDateTime;

public record UserConstraints(
        double currentSocPercent,
        double targetSocPercent,
        double batteryCapacityKwh,
        double maxChargingPowerKw,
        LocalDateTime plugInTime,
        LocalDateTime departureTime,
        String priceZone,
        double weightPrice,
        double weightCO2
) {
    public double energyRequiredKwh() {
        double socDelta = Math.max(0.0, targetSocPercent - currentSocPercent) / 100.0;
        return socDelta * batteryCapacityKwh;
    }
}
