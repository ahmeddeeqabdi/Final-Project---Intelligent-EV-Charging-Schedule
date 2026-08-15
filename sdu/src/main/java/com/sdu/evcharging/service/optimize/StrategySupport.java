package com.sdu.evcharging.service.optimize;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.sdu.evcharging.domain.strategy.GridData;
import com.sdu.evcharging.domain.strategy.UserConstraints;
import com.sdu.evcharging.dto.schedule.ScheduleResult;

final class StrategySupport {

    private StrategySupport() {
    }

    static ScheduleResult emptyResult() {
        return new ScheduleResult(List.of(), 0.0, 0.0);
    }

    static boolean isWithinWindow(LocalDateTime timestamp, UserConstraints constraints) {
        return !timestamp.isBefore(constraints.plugInTime())
                && timestamp.isBefore(constraints.departureTime());
    }

    static Weight normalizeWeights(double weightPriceInput, double weightCo2Input, double defaultWeight) {
        double weightPrice = Math.max(0.0, weightPriceInput);
        double weightCo2 = Math.max(0.0, weightCo2Input);
        double weightSum = weightPrice + weightCo2;
        if (weightSum <= 0.0) {
            return new Weight(defaultWeight, defaultWeight);
        }
        return new Weight(weightPrice / weightSum, weightCo2 / weightSum);
    }

    static Map<LocalDateTime, Double> buildHourlyCo2Lookup(List<GridData> co2Data) {
        if (co2Data == null || co2Data.isEmpty()) {
            return Map.of();
        }

        return co2Data.stream()
                .filter(Objects::nonNull)
                .filter(data -> data.timestamp() != null)
                .collect(Collectors.groupingBy(
                        data -> data.timestamp().truncatedTo(ChronoUnit.HOURS),
                        Collectors.averagingDouble(GridData::value)
                ));
    }

    static double averageGridValueOrDefault(List<GridData> data, double defaultValue) {
        if (data == null || data.isEmpty()) {
            return defaultValue;
        }
        return data.stream().mapToDouble(GridData::value).average().orElse(defaultValue);
    }

    record Weight(double priceWeight, double co2Weight) {
    }
}