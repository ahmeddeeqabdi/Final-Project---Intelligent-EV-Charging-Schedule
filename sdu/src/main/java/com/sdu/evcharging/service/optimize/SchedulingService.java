package com.sdu.evcharging.service.optimize;

import java.time.Duration;
import java.time.LocalDate;
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
import com.sdu.evcharging.domain.ScheduleResultEntity;
import com.sdu.evcharging.domain.ScheduleSlotEntity;
import com.sdu.evcharging.domain.User;
import com.sdu.evcharging.domain.strategy.ChargingStrategy;
import com.sdu.evcharging.domain.strategy.GridData;
import com.sdu.evcharging.domain.strategy.UserConstraints;
import com.sdu.evcharging.dto.schedule.ScheduleRequest;
import com.sdu.evcharging.dto.schedule.ScheduleResult;
import com.sdu.evcharging.repository.CO2IntensityRepository;
import com.sdu.evcharging.repository.EnergyPriceRepository;
import com.sdu.evcharging.repository.ScheduleResultRepository;
import com.sdu.evcharging.repository.UserRepository;
import com.sdu.evcharging.service.ingest.DataSyncService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SchedulingService {

    private static final String DEFAULT_STRATEGY_KEY = "naive";
    private static final double DEFAULT_CO2_VALUE = 0.0;
    private static final String PRICE_DEGRADE_REASON = "EDS API unavailable - serving cached historical prices";
    private static final String CO2_DEGRADE_REASON = "EDS API unavailable - serving cached historical CO2";
    private static final String CO2_MISSING_REASON = "CO2 data unavailable - using zero-default CO2 signal";

    private final EnergyPriceRepository energyPriceRepository;
    private final CO2IntensityRepository co2IntensityRepository;
    private final UserRepository userRepository;
    private final ScheduleResultRepository scheduleResultRepository;
    private final DataSyncService dataSyncService;
    private final Map<String, ChargingStrategy> strategies;

    public ScheduleResult createSchedule(ScheduleRequest request, String algorithm) {
        return createSchedule(request, algorithm, null);
    }

    public ScheduleResult createSchedule(ScheduleRequest request, String algorithm, Long userId) {
        Objects.requireNonNull(request, "request must not be null");

        PriceSelection priceSelection = loadPriceData(request);
        Co2Selection co2Selection = loadCo2Data(request);

        if (priceSelection.prices().isEmpty()) {
            throw new IllegalStateException(
                    "No price data found for zone=" + request.priceZone()
                    + " between " + request.plugInTime() + " and " + request.departureTime()
                    + ". Trigger a data sync first or wait for API recovery."
            );
        }

        String strategyKey = normalizeStrategyKey(algorithm);
        ChargingStrategy strategy = resolveStrategy(strategyKey);

        UserConstraints constraints = toConstraints(request);
        List<GridData> priceData = toPriceData(priceSelection.prices());
        List<GridData> co2Data = toHourlyCo2Data(co2Selection.values());

        log.info("Running [{}] scheduler for zone={}", strategyKey, request.priceZone());
        ScheduleResult base = strategy.solve(constraints, priceData, co2Data);
        ScheduleResult response = new ScheduleResult(
                base.slots(),
                base.totalPredictedCost(),
                base.totalPredictedEmissions(),
            mergeDegradedMode(priceSelection.degradedMode(), co2Selection.degradedMode())
        );

        if (userId != null) {
            persistSchedule(userId, strategyKey, response);
        }
        return response;
    }

        private void persistSchedule(Long userId, String algorithm, ScheduleResult result) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        ScheduleResultEntity scheduleEntity = ScheduleResultEntity.builder()
            .user(user)
            .algorithm(algorithm)
            .totalPredictedCost(result.totalPredictedCost())
            .totalPredictedEmissions(result.totalPredictedEmissions())
            .degradedEnabled(result.degradedMode().enabled())
            .degradedReason(result.degradedMode().reason())
            .degradedSource(result.degradedMode().source())
            .degradedDataAgeHours(result.degradedMode().dataAgeHours())
            .build();

        List<ScheduleSlotEntity> slotEntities = result.slots().stream()
            .map(slot -> ScheduleSlotEntity.builder()
                .timestamp(slot.timestamp())
                .powerDraw(slot.powerDraw())
                .currentPrice(slot.currentPrice())
                .currentCO2(slot.currentCO2())
                .build())
            .toList();

        scheduleEntity.setSlots(slotEntities);
        scheduleResultRepository.save(scheduleEntity);
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
                            ScheduleResult.DegradedMode.degraded(PRICE_DEGRADE_REASON, "cached-historical-price", dataAgeHours)
                    );
                })
                .orElseGet(() -> new PriceSelection(List.of(), ScheduleResult.DegradedMode.live()));
    }

    private Co2Selection loadCo2Data(ScheduleRequest request) {
        List<CO2Intensity> live = co2IntensityRepository.findByPriceAreaAndTimestampUtcBetweenOrderByTimestampUtcAsc(
                request.priceZone(),
                request.plugInTime(),
                request.departureTime()
        );

        if (!live.isEmpty()) {
            return new Co2Selection(live, ScheduleResult.DegradedMode.live());
        }

        // Try to refresh CO2 data on-demand for the request window before falling back to cached history.
        for (LocalDate date : requestedDates(request)) {
            try {
                dataSyncService.syncCO2Data(date, request.priceZone());
            } catch (Exception ex) {
                log.warn("On-demand CO2 sync failed [zone={}] [date={}] reason={}",
                        request.priceZone(), date, ex.getMessage());
            }
        }

        List<CO2Intensity> refreshed = co2IntensityRepository.findByPriceAreaAndTimestampUtcBetweenOrderByTimestampUtcAsc(
                request.priceZone(),
                request.plugInTime(),
                request.departureTime()
        );
        if (!refreshed.isEmpty()) {
            return new Co2Selection(refreshed, ScheduleResult.DegradedMode.live());
        }

        return co2IntensityRepository.findTopByPriceAreaOrderByTimestampUtcDesc(request.priceZone())
                .map(latest -> {
                    LocalDateTime sourceDayStart = latest.getTimestampUtc().toLocalDate().atStartOfDay();
                    LocalDateTime sourceDayEnd = sourceDayStart.plusDays(1);
                    List<CO2Intensity> sourceDay = co2IntensityRepository.findByPriceAreaAndTimestampUtcBetweenOrderByTimestampUtcAsc(
                            request.priceZone(),
                            sourceDayStart,
                            sourceDayEnd
                    );

                    if (sourceDay.isEmpty()) {
                        return new Co2Selection(
                                List.of(),
                                ScheduleResult.DegradedMode.degraded(CO2_MISSING_REASON, "co2-unavailable", 0)
                        );
                    }

                    long dataAgeHours = Math.max(0L, Duration.between(latest.getTimestampUtc(), request.plugInTime()).toHours());
                    log.warn("Serving cached historical CO2 in fallback mode [zone={}] sourceDay={}",
                            request.priceZone(), sourceDayStart.toLocalDate());
                    return new Co2Selection(
                            shiftCo2SeriesIntoRequestedWindow(sourceDay, request),
                            ScheduleResult.DegradedMode.degraded(CO2_DEGRADE_REASON, "cached-historical-co2", dataAgeHours)
                    );
                })
                .orElseGet(() -> new Co2Selection(
                        List.of(),
                        ScheduleResult.DegradedMode.degraded(CO2_MISSING_REASON, "co2-unavailable", 0)
                ));
    }

    private static List<LocalDate> requestedDates(ScheduleRequest request) {
        LocalDate startDate = request.plugInTime().toLocalDate();
        LocalDate endDate = request.departureTime().minusSeconds(1).toLocalDate();

        if (endDate.isBefore(startDate)) {
            return List.of(startDate);
        }

        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            dates.add(day);
        }
        return dates;
    }

    private static ScheduleResult.DegradedMode mergeDegradedMode(
            ScheduleResult.DegradedMode priceMode,
            ScheduleResult.DegradedMode co2Mode
    ) {
        if (!priceMode.enabled() && !co2Mode.enabled()) {
            return ScheduleResult.DegradedMode.live();
        }
        if (priceMode.enabled() && !co2Mode.enabled()) {
            return priceMode;
        }
        if (!priceMode.enabled()) {
            return co2Mode;
        }

        String reason = priceMode.reason() + " | " + co2Mode.reason();
        String source = priceMode.source() + "+" + co2Mode.source();
        long dataAgeHours = Math.max(
                priceMode.dataAgeHours() == null ? 0L : priceMode.dataAgeHours(),
                co2Mode.dataAgeHours() == null ? 0L : co2Mode.dataAgeHours()
        );

        return ScheduleResult.DegradedMode.degraded(reason, source, dataAgeHours);
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

    private static List<CO2Intensity> shiftCo2SeriesIntoRequestedWindow(List<CO2Intensity> sourceDay, ScheduleRequest request) {
        long slotsInWindow = Duration.between(request.plugInTime(), request.departureTime()).toHours();
        int slots = Math.max(1, (int) slotsInWindow);

        List<CO2Intensity> shifted = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) {
            CO2Intensity source = sourceDay.get(i % sourceDay.size());
            shifted.add(CO2Intensity.builder()
                    .timestampUtc(request.plugInTime().plusHours(i))
                    .priceArea(request.priceZone())
                    .gPerKwh(source.getGPerKwh())
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

        private record Co2Selection(
            List<CO2Intensity> values,
            ScheduleResult.DegradedMode degradedMode
        ) {

        }
}
