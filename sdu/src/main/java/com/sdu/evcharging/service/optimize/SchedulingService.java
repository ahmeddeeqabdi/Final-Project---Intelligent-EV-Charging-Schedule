package com.sdu.evcharging.service.optimize;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.sdu.evcharging.domain.EnergyPrice;
import com.sdu.evcharging.dto.schedule.ChargingSlot;
import com.sdu.evcharging.dto.schedule.ScheduleRequest;
import com.sdu.evcharging.repository.EnergyPriceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SchedulingService {

    private final EnergyPriceRepository energyPriceRepository;
    // Spring auto-collects all ChargingStrategy beans into this map
    // keyed by the bean name (e.g. "naive", "greedy", "dp")
    private final Map<String, ChargingStrategy> strategies;

    public List<ChargingSlot> createSchedule(ScheduleRequest request, String algorithm) {
        List<EnergyPrice> prices = energyPriceRepository
                .findByPriceAreaAndHourUtcBetweenOrderByHourUtcAsc(
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

        ChargingStrategy strategy = strategies.getOrDefault(
                algorithm, strategies.get("naive")
        );
        log.info("Running [{}] scheduler for zone={}", strategy.name(), request.priceZone());

        return strategy.schedule(request, prices);
    }
}