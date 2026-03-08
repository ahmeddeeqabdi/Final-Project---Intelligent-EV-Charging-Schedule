package com.sdu.evcharging.service.optimize;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.sdu.evcharging.domain.EnergyPrice;
import com.sdu.evcharging.dto.schedule.ChargingSlot;
import com.sdu.evcharging.dto.schedule.ScheduleRequest;

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

    @Override
    public String name() {
        return "naive";
    }

    @Override
    public List<ChargingSlot> schedule(
            ScheduleRequest request,
            List<EnergyPrice> availablePrices
    ) {
        double energyNeededKwh = calculateEnergyNeeded(request);
        log.info("[NaiveScheduler] Energy required: {} kWh over {} slots",
                String.format("%.2f", energyNeededKwh), availablePrices.size());

        List<ChargingSlot> slots = new ArrayList<>();
        double remainingKwh = energyNeededKwh;

        for (EnergyPrice price : availablePrices) {
            if (remainingKwh <= 0.001) break; // tolerance for floating-point

            // Fill this slot fully up to max power, or just what's left
            double energyThisSlot = Math.min(request.maxChargingPowerKw(), remainingKwh);
            double costThisSlot   = energyThisSlot * price.getPriceDkkPerKwh();

            slots.add(new ChargingSlot(
                    price.getHourUtc(),
                    price.getHourUtc().plusHours(1),
                    request.maxChargingPowerKw(),
                    energyThisSlot,
                    costThisSlot,
                    0.0  // CO2 per slot added in Week 4 when CO2 data is joined
            ));

            remainingKwh -= energyThisSlot;
        }

        if (remainingKwh > 0.001) {
            log.warn("[NaiveScheduler] Could not fulfil full requirement. " +
                     "{} kWh unscheduled — time window too short?",
                     String.format("%.2f", remainingKwh));
        }

        double totalCost = slots.stream().mapToDouble(ChargingSlot::estimatedCostDKK).sum();
        log.info("[NaiveScheduler] Schedule complete: {} slots, {:.2f} DKK estimated cost"
                .replace("{:.2f}", String.format("%.2f", totalCost)), slots.size());

        return slots;
    }

    private double calculateEnergyNeeded(ScheduleRequest req) {
        double socDelta = (req.targetSocPercent() - req.currentSocPercent()) / 100.0;
        return socDelta * req.batteryCapacityKwh();
    }
}