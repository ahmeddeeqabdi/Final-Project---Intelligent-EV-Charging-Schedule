package com.sdu.evcharging.service.optimize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.sdu.evcharging.domain.strategy.ChargingStrategy;
import com.sdu.evcharging.domain.strategy.GridData;
import com.sdu.evcharging.domain.strategy.UserConstraints;
import com.sdu.evcharging.dto.schedule.ChargingSlot;
import com.sdu.evcharging.dto.schedule.ScheduleResult;

import lombok.extern.slf4j.Slf4j;

/**
 * Naive (Baseline) Scheduler.
 *
 * Strategy: Charge immediately from plug-in time at max power
 * until the required energy is delivered. Ignores all cost/CO2 signals.
 *
 * Complexity: O(N) — iterates over slots once.
 * Purpose: Establishes the baseline for cost/CO2 comparison.
 */
@Component("naive")
@Slf4j
public class NaiveScheduler implements ChargingStrategy {

    private static final double ENERGY_TOLERANCE = 1e-3;
    private static final double DEFAULT_CO2_VALUE = 0.0;

    @Override
    public ScheduleResult solve(
            UserConstraints constraints,
            List<GridData> priceData,
            List<GridData> co2Data
    ) {
        if (priceData == null || priceData.isEmpty()) {
            log.info("[NaiveScheduler] No price data available. Returning empty schedule.");
            return new ScheduleResult(List.of(), 0.0, 0.0);
        }

        double energyNeededKwh = constraints.energyRequiredKwh();
        log.info("[NaiveScheduler] Energy required: {} kWh over {} slots",
                energyNeededKwh, priceData.size());

        if (energyNeededKwh <= ENERGY_TOLERANCE) {
            return new ScheduleResult(List.of(), 0.0, 0.0);
        }

        Map<LocalDateTime, Double> co2ByTime = buildCo2ByTime(co2Data);

        List<ChargingSlot> slots = new ArrayList<>();
        double remainingKwh = energyNeededKwh;

        for (GridData price : priceData) {
            if (remainingKwh <= ENERGY_TOLERANCE) {
                break;
            }

            if (!isWithinChargingWindow(price.timestamp(), constraints)) {
                continue;
            }

            double energyThisSlot = Math.min(constraints.maxChargingPowerKw(), remainingKwh);
            double co2Value = co2ByTime.getOrDefault(price.timestamp(), DEFAULT_CO2_VALUE);

            slots.add(new ChargingSlot(
                    price.timestamp(),
                    energyThisSlot,
                    price.value(),
                    co2Value
            ));

            remainingKwh -= energyThisSlot;
        }

        if (remainingKwh > ENERGY_TOLERANCE) {
            log.warn("[NaiveScheduler] Could not fulfil full requirement. {} kWh unscheduled - time window too short?",
                    remainingKwh);
        }

        slots.sort(Comparator.comparing(ChargingSlot::timestamp));

        double totalCost = calculateTotalCost(slots);
        double totalEmissions = calculateTotalEmissions(slots);

        log.info("[NaiveScheduler] Schedule complete: {} slots, {} DKK estimated cost", slots.size(), totalCost);

        return new ScheduleResult(slots, totalCost, totalEmissions);
    }

    private static Map<LocalDateTime, Double> buildCo2ByTime(List<GridData> co2Data) {
        Map<LocalDateTime, Double> co2ByTime = new HashMap<>();
        if (co2Data == null) {
            return co2ByTime;
        }

        for (GridData data : co2Data) {
            co2ByTime.putIfAbsent(data.timestamp(), data.value());
        }
        return co2ByTime;
    }

    private static boolean isWithinChargingWindow(LocalDateTime timestamp, UserConstraints constraints) {
        return !timestamp.isBefore(constraints.plugInTime())
                && timestamp.isBefore(constraints.departureTime());
    }

    private static double calculateTotalCost(List<ChargingSlot> slots) {
        return slots.stream()
                .mapToDouble(slot -> slot.powerDraw() * slot.currentPrice())
                .sum();
    }

    private static double calculateTotalEmissions(List<ChargingSlot> slots) {
        return slots.stream()
                .mapToDouble(slot -> slot.powerDraw() * slot.currentCO2())
                .sum();
    }
}