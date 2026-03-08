package com.sdu.evcharging.dto.schedule;

import java.time.LocalDateTime;

public record ChargingSlot(
        LocalDateTime startTime,
        LocalDateTime endTime,
        double powerKw,
        double energyKwh,
        double estimatedCostDKK,
        double estimatedCO2Grams
) {}