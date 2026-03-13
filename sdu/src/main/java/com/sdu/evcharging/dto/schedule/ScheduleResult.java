package com.sdu.evcharging.dto.schedule;

import java.util.List;

public record ScheduleResult(
        List<ChargingSlot> slots,
        double totalPredictedCost,
        double totalPredictedEmissions
) {}
