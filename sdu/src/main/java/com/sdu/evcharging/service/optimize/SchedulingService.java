package com.sdu.evcharging.service.optimize;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
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
    private static final Set<String> ALLOWED_ZONES = Set.of("DK1", "DK2");
    private static final String DEFAULT_PRICE_AREA = "DK2";

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

        ScheduleRequest effectiveRequest = resolvePriceArea(request, userId);

        PriceSelection priceSelection = loadPriceData(effectiveRequest);
        Co2Selection co2Selection = loadCo2Data(effectiveRequest);

        if (priceSelection.prices().isEmpty()) {
            throw new IllegalStateException(
                    "No price data found for zone=" + effectiveRequest.priceZone()
                    + " between " + effectiveRequest.plugInTime() + " and " + effectiveRequest.departureTime()
                    + ". Trigger a data sync first or wait for API recovery."
            );
        }

        String strategyKey = normalizeStrategyKey(algorithm);
        ChargingStrategy strategy = resolveStrategy(strategyKey);

        UserConstraints constraints = toConstraints(effectiveRequest);
        List<GridData> priceData = toPriceData(priceSelection.prices());
        List<GridData> co2Data = toHourlyCo2Data(co2Selection.values());
        List<ScheduleResult.MarketSignal> marketSignals = buildMarketSignals(effectiveRequest, priceData, co2Data);

        log.info("Running [{}] scheduler for zone={}", strategyKey, effectiveRequest.priceZone());
        ScheduleResult base = strategy.solve(constraints, priceData, co2Data);
        ScheduleResult response = new ScheduleResult(
                base.slots(),
                base.totalPredictedCost(),
                base.totalPredictedEmissions(),
                mergeDegradedMode(priceSelection.degradedMode(), co2Selection.degradedMode()),
                marketSignals
        );

        if (userId != null) {
            persistSchedule(userId, strategyKey, response);
        }
        return response;
    }

    private ScheduleRequest resolvePriceArea(ScheduleRequest request, Long userId) {
        String zone = normalizeZone(request.priceZone());

        if (zone == null) {
            if (userId == null) {
                throw new IllegalArgumentException("Zone must be DK1 or DK2");
            }

            zone = userRepository.findById(userId)
                    .map(User::getConstraints)
                    .map(com.sdu.evcharging.domain.UserConstraints::getPriceArea)
                    .map(SchedulingService::normalizeZone)
                    .orElse(DEFAULT_PRICE_AREA);
        }

        return new ScheduleRequest(
                request.currentSocPercent(),
                request.targetSocPercent(),
                request.batteryCapacityKwh(),
                request.maxChargingPowerKw(),
                request.plugInTime(),
                request.departureTime(),
                zone,
                request.weightPrice(),
                request.weightCO2()
        );
    }

    private static String normalizeZone(String zone) {
        if (zone == null || zone.isBlank()) {
            return null;
        }

        String normalized = zone.trim().toUpperCase();
        if (!ALLOWED_ZONES.contains(normalized)) {
            throw new IllegalArgumentException("Zone must be DK1 or DK2");
        }
        return normalized;
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

        int requestedSlots = requestedHourSlots(request);
        Map<LocalDateTime, Double> hourly = toHourlyAveragesByHour(live);
        if (hasFullCoverage(hourly, request, requestedSlots)) {
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
        Map<LocalDateTime, Double> refreshedHourly = toHourlyAveragesByHour(refreshed);
        if (hasFullCoverage(refreshedHourly, request, requestedSlots)) {
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

                    Map<LocalDateTime, Double> sourceHourly = toHourlyAveragesByHour(sourceDay);
                    if (sourceHourly.isEmpty()) {
                        return new Co2Selection(
                                List.of(),
                                ScheduleResult.DegradedMode.degraded(CO2_MISSING_REASON, "co2-unavailable", 0)
                        );
                    }

                    List<CO2Intensity> completed = completeCo2Window(
                            request,
                            refreshedHourly,
                            sourceHourly,
                            requestedSlots
                    );

                    long dataAgeHours = Math.max(0L, Duration.between(latest.getTimestampUtc(), request.plugInTime()).toHours());
                    log.warn("Serving cached historical CO2 in fallback mode [zone={}] sourceDay={}",
                            request.priceZone(), sourceDayStart.toLocalDate());
                    return new Co2Selection(
                            completed,
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

    private static int requestedHourSlots(ScheduleRequest request) {
        long slotsInWindow = Duration.between(request.plugInTime(), request.departureTime()).toHours();
        return Math.max(1, (int) slotsInWindow);
    }

    private static boolean hasFullCoverage(Map<LocalDateTime, Double> hourly, ScheduleRequest request, int slots) {
        if (hourly.isEmpty()) {
            return false;
        }

        for (int i = 0; i < slots; i++) {
            LocalDateTime slotTime = request.plugInTime().plusHours(i).withMinute(0).withSecond(0).withNano(0);
            if (!hourly.containsKey(slotTime)) {
                return false;
            }
        }
        return true;
    }

    private static Map<LocalDateTime, Double> toHourlyAveragesByHour(List<CO2Intensity> series) {
        if (series == null || series.isEmpty()) {
            return Map.of();
        }

        return series.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getTimestampUtc().withMinute(0).withSecond(0).withNano(0),
                        Collectors.averagingDouble(CO2Intensity::getGPerKwh)
                ));
    }

    private static List<CO2Intensity> completeCo2Window(
            ScheduleRequest request,
            Map<LocalDateTime, Double> liveHourly,
            Map<LocalDateTime, Double> sourceHourly,
            int slots
    ) {
        List<Map.Entry<LocalDateTime, Double>> sourcePattern = sourceHourly.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();

        List<CO2Intensity> completed = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) {
            LocalDateTime slotTime = request.plugInTime().plusHours(i).withMinute(0).withSecond(0).withNano(0);
            Double value = liveHourly.get(slotTime);
            if (value == null) {
                value = sourcePattern.get(i % sourcePattern.size()).getValue();
            }

            completed.add(CO2Intensity.builder()
                    .timestampUtc(slotTime)
                    .priceArea(request.priceZone())
                    .gPerKwh(value)
                    .build());
        }

        return completed;
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
        Long priceAgeHours = priceMode.dataAgeHours();
        Long co2AgeHours = co2Mode.dataAgeHours();
        long safePriceAgeHours = priceAgeHours != null ? priceAgeHours : 0L;
        long safeCo2AgeHours = co2AgeHours != null ? co2AgeHours : 0L;
        long dataAgeHours = Math.max(
            safePriceAgeHours,
            safeCo2AgeHours
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

        private static List<ScheduleResult.MarketSignal> buildMarketSignals(
            ScheduleRequest request,
            List<GridData> priceData,
            List<GridData> co2Data
        ) {
        Map<LocalDateTime, Double> priceByTime = priceData.stream()
            .collect(Collectors.toMap(GridData::timestamp, GridData::value, (left, _right) -> left));
        Map<LocalDateTime, Double> co2ByTime = co2Data.stream()
            .collect(Collectors.toMap(GridData::timestamp, GridData::value, (left, _right) -> left));

        TreeSet<LocalDateTime> timestamps = new TreeSet<>();
        timestamps.addAll(priceByTime.keySet());
        timestamps.addAll(co2ByTime.keySet());

        return timestamps.stream()
            .filter(timestamp -> !timestamp.isBefore(request.plugInTime()))
            .filter(timestamp -> timestamp.isBefore(request.departureTime()))
            .map(timestamp -> new ScheduleResult.MarketSignal(
                timestamp,
                priceByTime.get(timestamp),
                co2ByTime.get(timestamp)
            ))
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
            .collect(Collectors.groupingBy(
                i -> i.getTimestampUtc().withMinute(0).withSecond(0).withNano(0),
                LinkedHashMap::new,
                Collectors.toList()
            ));

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
