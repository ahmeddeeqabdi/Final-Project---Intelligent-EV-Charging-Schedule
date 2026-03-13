package com.sdu.evcharging.service.optimize;

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

    @Override
    public ScheduleResult solve(
            UserConstraints constraints,
            List<GridData> priceData,
            List<GridData> co2Data
    ) {
        double energyNeededKwh = constraints.energyRequiredKwh();
        log.info("[NaiveScheduler] Energy required: {} kWh over {} slots",
                String.format("%.2f", energyNeededKwh), priceData.size());

        if (priceData.isEmpty() || energyNeededKwh <= ENERGY_TOLERANCE) {
            return new ScheduleResult(List.of(), 0.0, 0.0);
        }

        Map<java.time.LocalDateTime, Double> co2ByTime = new HashMap<>();
        if (co2Data != null) {
            for (GridData data : co2Data) {
                co2ByTime.putIfAbsent(data.timestamp(), data.value());
            }
        }

        List<ChargingSlot> slots = new ArrayList<>();
        double remainingKwh = energyNeededKwh;

        for (GridData price : priceData) {
            if (remainingKwh <= ENERGY_TOLERANCE) {
                break;
            }
            if (price.timestamp().isBefore(constraints.plugInTime())
                    || !price.timestamp().isBefore(constraints.departureTime())) {
                continue;
            }

            double energyThisSlot = Math.min(constraints.maxChargingPowerKw(), remainingKwh);
            double co2Value = co2ByTime.getOrDefault(price.timestamp(), 0.0);

            slots.add(new ChargingSlot(
                    price.timestamp(),
                    energyThisSlot,
                    price.value(),
                    co2Value
            ));

            remainingKwh -= energyThisSlot;
        }

        if (remainingKwh > ENERGY_TOLERANCE) {
            log.warn("[NaiveScheduler] Could not fulfil full requirement. " +
                     "{} kWh unscheduled - time window too short?",
                     String.format("%.2f", remainingKwh));
        }

        slots.sort(Comparator.comparing(ChargingSlot::timestamp));

        double totalCost = slots.stream().mapToDouble(slot -> slot.powerDraw() * slot.currentPrice()).sum();
        double totalEmissions = slots.stream().mapToDouble(slot -> slot.powerDraw() * slot.currentCO2()).sum();

        log.info("[NaiveScheduler] Schedule complete: {} slots, {:.2f} DKK estimated cost"
                .replace("{:.2f}", String.format("%.2f", totalCost)), slots.size());

        return new ScheduleResult(slots, totalCost, totalEmissions);
    }
}