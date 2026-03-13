package com.sdu.evcharging.service.optimize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private final EnergyPriceRepository energyPriceRepository;
    private final CO2IntensityRepository co2IntensityRepository;
    private final Map<String, ChargingStrategy> strategies;

    public ScheduleResult createSchedule(ScheduleRequest request, String algorithm) {
        List<EnergyPrice> prices = energyPriceRepository
                .findByPriceAreaAndHourUtcBetweenOrderByHourUtcAsc(
                        request.priceZone(),
                        request.plugInTime(),
                        request.departureTime()
                );
    List<CO2Intensity> co2Series = co2IntensityRepository
        .findByPriceAreaAndTimestampUtcBetweenOrderByTimestampUtcAsc(
            request.priceZone(),
            request.plugInTime(),
            request.departureTime()
        );

        if (prices.isEmpty()) {
            throw new IllegalStateException(
                "No price data found for zone=" + request.priceZone() +
                " between " + request.plugInTime() + " and " + request.departureTime() +
                ". Trigger a data sync first."
            );
        }

        ChargingStrategy strategy = strategies.get(algorithm);
        if (strategy == null) {
            strategy = strategies.get("naive");
        }

        if (strategy == null) {
            throw new IllegalStateException("No scheduling strategy is registered.");
        }

        UserConstraints constraints = new UserConstraints(
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

        List<GridData> priceData = prices.stream()
                .map(price -> new GridData(price.getHourUtc(), price.getPriceDkkPerKwh()))
                .toList();

        List<GridData> co2Data = toHourlyCo2Data(co2Series);

        log.info("Running [{}] scheduler for zone={}", algorithm, request.priceZone());
        return strategy.solve(constraints, priceData, co2Data);
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
                    .orElse(0.0);
            hourly.add(new GridData(entry.getKey(), avg));
        }
        hourly.sort(java.util.Comparator.comparing(GridData::timestamp));
        return hourly;
    }
}