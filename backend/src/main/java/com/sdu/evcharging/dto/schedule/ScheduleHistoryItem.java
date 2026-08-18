package com.sdu.evcharging.dto.schedule;

import java.time.OffsetDateTime;
import java.util.List;

public record ScheduleHistoryItem(
        Long id,
        String algorithm,
        double totalPredictedCost,
        double totalPredictedEmissions,
        ScheduleResult.DegradedMode degradedMode,
        OffsetDateTime createdAt,
        List<ChargingSlot> slots
) {
}
