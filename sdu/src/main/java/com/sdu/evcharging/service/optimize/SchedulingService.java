package com.sdu.evcharging.service.optimize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.sdu.evcharging.domain.CO2Intensity;
import com.sdu.evcharging.domain.EnergyPrice;
import com.sdu.evcharging.domain.strategy.ChargingStrategy;
import com.sdu.evcharging.domain.strategy.GridData;
import com.sdu.evcharging.domain.strategy.UserConstraints;
import com.sdu.evcharging.dto.schedule.ScheduleRequest;
import com.sdu.evcharging.dto.schedule.ScheduleResult;
import com.sdu.evcharging.repository.CO2IntensityRepository;
import com.sdu.evcharging.repository.EnergyPriceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SchedulingService {

    private static final String DEFAULT_STRATEGY_KEY = "naive";
    private static final double DEFAULT_CO2_VALUE = 0.0;

    private final EnergyPriceRepository energyPriceRepository;
    private final CO2IntensityRepository co2IntensityRepository;
    private final Map<String, ChargingStrategy> strategies;

    public ScheduleResult createSchedule(ScheduleRequest request, String algorithm) {
        Objects.requireNonNull(request, "request must not be null");

        List<EnergyPrice> prices = loadPriceData(request);
        List<CO2Intensity> co2Series = loadCo2Data(request);

        if (prices.isEmpty()) {
            throw new IllegalStateException(
                    "No price data found for zone=" + request.priceZone() +
                            " between " + request.plugInTime() + " and " + request.departureTime() +
                            ". Trigger a data sync first."
            );
        }

        String strategyKey = normalizeStrategyKey(algorithm);
        ChargingStrategy strategy = resolveStrategy(strategyKey);

        UserConstraints constraints = toConstraints(request);
        List<GridData> priceData = toPriceData(prices);
        List<GridData> co2Data = toHourlyCo2Data(co2Series);

        log.info("Running [{}] scheduler for zone={}", strategyKey, request.priceZone());
        return strategy.solve(constraints, priceData, co2Data);
    }

    private List<EnergyPrice> loadPriceData(ScheduleRequest request) {
        return energyPriceRepository.findByPriceAreaAndHourUtcBetweenOrderByHourUtcAsc(
                request.priceZone(),
                request.plugInTime(),
                request.departureTime()
        );
    }

    private List<CO2Intensity> loadCo2Data(ScheduleRequest request) {
        return co2IntensityRepository.findByPriceAreaAndTimestampUtcBetweenOrderByTimestampUtcAsc(
                request.priceZone(),
                request.plugInTime(),
                request.departureTime()
        );
    }

    private ChargingStrategy resolveStrategy(String strategyKey) {
        ChargingStrategy strategy = strategies.get(strategyKey);
        if (strategy != null) {
            return strategy;
        }

        log.warn("Strategy [{}] not found. Falling back to [{}].", strategyKey, DEFAULT_STRATEGY_KEY);
        ChargingStrategy fallback = strategies.get(DEFAULT_STRATEGY_KEY);
        if (fallback == null) {
            throw new IllegalStateException("No scheduling strategy is registered.");
        }
        return fallback;
    }

    private static String normalizeStrategyKey(String algorithm) {
        if (algorithm == null || algorithm.isBlank()) {
            return DEFAULT_STRATEGY_KEY;
        }
        return algorithm.trim();
    }

    private static UserConstraints toConstraints(ScheduleRequest request) {
        return new UserConstraints(
                request.currentSocPercent(),
                request.targetSocPercent(),
                request.batteryCapacityKwh(),
                request.maxChargingPowerKw(),
                request.plugInTime(),
                request.departureTime(),
                request.priceZone(),
                request.weightPrice(),
                request.weightCO2()
        );
    }

    private static List<GridData> toPriceData(List<EnergyPrice> prices) {
        return prices.stream()
                .map(price -> new GridData(price.getHourUtc(), price.getPriceDkkPerKwh()))
                .toList();
    }

    private List<GridData> toHourlyCo2Data(List<CO2Intensity> co2Series) {
        if (co2Series == null || co2Series.isEmpty()) {
            return List.of();
        }

        Map<LocalDateTime, List<CO2Intensity>> byHour = co2Series.stream()
                .collect(Collectors.groupingBy(i -> i.getTimestampUtc().withMinute(0).withSecond(0).withNano(0)));

        List<GridData> hourly = new ArrayList<>(byHour.size());
        for (Map.Entry<LocalDateTime, List<CO2Intensity>> entry : byHour.entrySet()) {
            double avg = entry.getValue().stream()
                    .mapToDouble(CO2Intensity::getGPerKwh)
                    .average()
                    .orElse(DEFAULT_CO2_VALUE);
            hourly.add(new GridData(entry.getKey(), avg));
        }

        hourly.sort(Comparator.comparing(GridData::timestamp));
        return hourly;
    }
}