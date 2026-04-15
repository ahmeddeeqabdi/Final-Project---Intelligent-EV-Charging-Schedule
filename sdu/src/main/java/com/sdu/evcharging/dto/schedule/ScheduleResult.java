package com.sdu.evcharging.dto.schedule;

import java.time.LocalDateTime;
import java.util.List;

public record ScheduleResult(
        List<ChargingSlot> slots,
        double totalPredictedCost,
        double totalPredictedEmissions,
        DegradedMode degradedMode,
        List<MarketSignal> marketSignals
) {
    public ScheduleResult(List<ChargingSlot> slots, double totalPredictedCost, double totalPredictedEmissions) {
        this(slots, totalPredictedCost, totalPredictedEmissions, DegradedMode.live(), List.of());
    }

    public ScheduleResult(
            List<ChargingSlot> slots,
            double totalPredictedCost,
            double totalPredictedEmissions,
            DegradedMode degradedMode
    ) {
        this(slots, totalPredictedCost, totalPredictedEmissions, degradedMode, List.of());
    }

    public record DegradedMode(
            boolean enabled,
            String reason,
            String source,
            Long dataAgeHours
    ) {
        public static DegradedMode live() {
            return new DegradedMode(false, null, "live", null);
        }

        public static DegradedMode degraded(String reason, String source, long dataAgeHours) {
            return new DegradedMode(true, reason, source, Math.max(0L, dataAgeHours));
        }
    }

    public record MarketSignal(
            LocalDateTime timestamp,
            Double energyPrice,
            Double co2Intensity
    ) {
    }
}
