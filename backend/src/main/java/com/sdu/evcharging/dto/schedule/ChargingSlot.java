package com.sdu.evcharging.dto.schedule;

import java.time.LocalDateTime;

public record ChargingSlot(
        LocalDateTime timestamp,
        double powerDraw,
        double currentPrice,
        double currentCO2
) {}