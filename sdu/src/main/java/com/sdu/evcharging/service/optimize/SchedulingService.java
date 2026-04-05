package com.sdu.evcharging.service.optimize;

import java.time.Duration;
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
    private static final String DEGRADE_REASON = "EDS API unavailable - serving cached historical prices";

    private final EnergyPriceRepository energyPriceRepository;
    private final CO2IntensityRepository co2IntensityRepository;
    private final Map<String, ChargingStrategy> strategies;

    public ScheduleResult createSchedule(ScheduleRequest request, String algorithm) {
        Objects.requireNonNull(request, "request must not be null");

        PriceSelection priceSelection = loadPriceData(request);
        List<CO2Intensity> co2Series = loadCo2Data(request);

        if (priceSelection.prices().isEmpty()) {
            throw new IllegalStateException(
                    "No price data found for zone=" + request.priceZone() +
                            " between " + request.plugInTime() + " and " + request.departureTime() +
                            ". Trigger a data sync first or wait for API recovery."
            );
        }

        String strategyKey = normalizeStrategyKey(algorithm);
        ChargingStrategy strategy = resolveStrategy(strategyKey);

        UserConstraints constraints = toConstraints(request);
        List<GridData> priceData = toPriceData(priceSelection.prices());
        List<GridData> co2Data = toHourlyCo2Data(co2Series);

        log.info("Running [{}] scheduler for zone={}", strategyKey, request.priceZone());
        ScheduleResult base = strategy.solve(constraints, priceData, co2Data);
        return new ScheduleResult(
            base.slots(),
            base.totalPredictedCost(),
            base.totalPredictedEmissions(),
            priceSelection.degradedMode()
        );
    }

        private PriceSelection loadPriceData(ScheduleRequest request) {
        List<EnergyPrice> live = energyPriceRepository.findByPriceAreaAndHourUtcBetweenOrderByHourUtcAsc(
                request.priceZone(),
                request.plugInTime(),
                request.departureTime()
        );
        if (!live.isEmpty()) {
            return new PriceSelection(live, ScheduleResult.DegradedMode.live());
        }

        return energyPriceRepository.findTopByPriceAreaOrderByHourUtcDesc(request.priceZone())
            .map(latest -> {
                LocalDateTime sourceDayStart = latest.getHourUtc().toLocalDate().atStartOfDay();
                LocalDateTime sourceDayEnd = sourceDayStart.plusDays(1);
                List<EnergyPrice> sourceDay = energyPriceRepository.findByPriceAreaAndHourUtcBetweenOrderByHourUtcAsc(
                    request.priceZone(),
                    sourceDayStart,
                    sourceDayEnd
                );

                if (sourceDay.isEmpty()) {
                return new PriceSelection(List.of(), ScheduleResult.DegradedMode.live());
                }

                List<EnergyPrice> shifted = shiftPriceSeriesIntoRequestedWindow(sourceDay, request);
                long dataAgeHours = Math.max(0L, Duration.between(latest.getHourUtc(), request.plugInTime()).toHours());

                log.warn("Serving cached historical prices in degraded mode [zone={}] sourceDay={} dataAgeHours={}",
                    request.priceZone(), sourceDayStart.toLocalDate(), dataAgeHours);
                return new PriceSelection(
                    shifted,
                    ScheduleResult.DegradedMode.degraded(DEGRADE_REASON, "cached-historical", dataAgeHours)
                );
            })
            .orElseGet(() -> new PriceSelection(List.of(), ScheduleResult.DegradedMode.live()));
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

    private static List<EnergyPrice> shiftPriceSeriesIntoRequestedWindow(List<EnergyPrice> sourceDay, ScheduleRequest request) {
        long slotsInWindow = Duration.between(request.plugInTime(), request.departureTime()).toHours();
        int slots = Math.max(1, (int) slotsInWindow);

        List<EnergyPrice> shifted = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) {
            EnergyPrice source = sourceDay.get(i % sourceDay.size());
            shifted.add(EnergyPrice.builder()
                    .hourUtc(request.plugInTime().plusHours(i))
                    .priceArea(request.priceZone())
                    .priceDkkPerKwh(source.getPriceDkkPerKwh())
                    .build());
        }

        return shifted;
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

    private record PriceSelection(
            List<EnergyPrice> prices,
            ScheduleResult.DegradedMode degradedMode
    ) {
    }
}