package com.sdu.evcharging.dto.schedule;

import java.util.List;

public record ScheduleResult(
        List<ChargingSlot> slots,
        double totalPredictedCost,
                double totalPredictedEmissions,
                DegradedMode degradedMode
) {
        public ScheduleResult(List<ChargingSlot> slots, double totalPredictedCost, double totalPredictedEmissions) {
                this(slots, totalPredictedCost, totalPredictedEmissions, DegradedMode.live());
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
}
