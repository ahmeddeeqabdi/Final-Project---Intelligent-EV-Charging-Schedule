package com.sdu.evcharging.service.optimize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.sdu.evcharging.domain.strategy.ChargingStrategy;
import com.sdu.evcharging.domain.strategy.GridData;
import com.sdu.evcharging.domain.strategy.UserConstraints;
import com.sdu.evcharging.dto.schedule.ChargingSlot;
import com.sdu.evcharging.dto.schedule.ScheduleResult;

@Component("greedy")
public class GreedyChargingStrategy implements ChargingStrategy {

    private static final double ENERGY_TOLERANCE = 1e-3;
    private static final double STEP_ROUNDING_EPSILON = 1e-9;
    private static final double DEFAULT_WEIGHT = 0.5;
    private static final double DEFAULT_CO2 = 0.0;
    private static final double STEP_SIZE_KWH = 0.5;
    private static final double SLOT_DURATION_HOURS = 1.0;

    @Override
    public ScheduleResult solve(UserConstraints constraints, List<GridData> priceData, List<GridData> co2Data) {
        Objects.requireNonNull(constraints, "constraints must not be null");

        if (priceData == null || priceData.isEmpty()) {
            return StrategySupport.emptyResult();
        }

        double energyRequiredKwh = constraints.energyRequiredKwh();
        if (energyRequiredKwh <= ENERGY_TOLERANCE) {
            return StrategySupport.emptyResult();
        }
        int totalSteps = toRoundedSteps(energyRequiredKwh);
        int maxStepsPerSlot = toMaxStepsPerSlot(constraints.maxChargingPowerKw());
        if (maxStepsPerSlot <= 0) {
            throw new IllegalArgumentException("Max charging power is too low for the configured step size.");
        }

        Map<LocalDateTime, Double> co2ByTime = StrategySupport.buildHourlyCo2Lookup(co2Data);
        double defaultCo2 = StrategySupport.averageGridValueOrDefault(co2Data, DEFAULT_CO2);
        List<CandidateSlot> candidates = buildCandidates(constraints, priceData, co2ByTime, defaultCo2);

        if (candidates.isEmpty()) {
            return StrategySupport.emptyResult();
        }

        long maxDeliverableSteps = (long) maxStepsPerSlot * candidates.size();
        if (maxDeliverableSteps < totalSteps) {
            throw new IllegalArgumentException("Charging window is infeasible for the required energy and max power.");
        }

        StrategySupport.Weight normalizedWeights = StrategySupport.normalizeWeights(
            constraints.weightPrice(),
            constraints.weightCO2(),
            DEFAULT_WEIGHT);
        List<ScoredCandidateSlot> scoredCandidates = scoreAndSortCandidates(candidates, normalizedWeights);
        List<ChargingSlot> slots = allocateSlots(scoredCandidates, maxStepsPerSlot, totalSteps);

        slots.sort(Comparator.comparing(ChargingSlot::timestamp));

        double totalCost = calculateTotalCost(slots);
        double totalEmissions = calculateTotalEmissions(slots);
        return new ScheduleResult(slots, totalCost, totalEmissions);
    }

    private static int toRoundedSteps(double energyRequiredKwh) {
        double rawSteps = energyRequiredKwh / STEP_SIZE_KWH;
        return (int) Math.max(1, Math.ceil(rawSteps - STEP_ROUNDING_EPSILON));
    }

    private static int toMaxStepsPerSlot(double maxChargingPowerKw) {
        double maxEnergyPerSlot = maxChargingPowerKw * SLOT_DURATION_HOURS;
        return (int) Math.floor((maxEnergyPerSlot / STEP_SIZE_KWH) + STEP_ROUNDING_EPSILON);
    }

    private static List<CandidateSlot> buildCandidates(
            UserConstraints constraints,
            List<GridData> priceData,
            Map<LocalDateTime, Double> co2ByTime,
            double defaultCo2
    ) {
        List<CandidateSlot> candidates = new ArrayList<>();
        for (GridData pricePoint : priceData) {
            LocalDateTime timestamp = pricePoint.timestamp();
            if (!isWithinWindow(timestamp, constraints)) {
                continue;
            }

            LocalDateTime hourBucket = timestamp.withMinute(0).withSecond(0).withNano(0);
            double co2 = co2ByTime.getOrDefault(hourBucket, defaultCo2);
            candidates.add(new CandidateSlot(timestamp, pricePoint.value(), co2));
        }
        return candidates;
    }

    private static boolean isWithinWindow(LocalDateTime timestamp, UserConstraints constraints) {
        return StrategySupport.isWithinWindow(timestamp, constraints);
    }

    private static List<ScoredCandidateSlot> scoreAndSortCandidates(
            List<CandidateSlot> candidates,
            StrategySupport.Weight weight
    ) {
        List<Double> prices = candidates.stream().map(CandidateSlot::price).toList();
        List<Double> co2Values = candidates.stream().map(CandidateSlot::co2).toList();
        List<Double> normalizedPrices = NormalizationUtility.minMaxNormalize(prices);
        List<Double> normalizedCo2 = NormalizationUtility.minMaxNormalize(co2Values);

        List<ScoredCandidateSlot> scored = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            double score = (weight.priceWeight() * normalizedPrices.get(i))
                    + (weight.co2Weight() * normalizedCo2.get(i));
            scored.add(new ScoredCandidateSlot(candidates.get(i), score));
        }

        scored.sort(Comparator.comparingDouble(ScoredCandidateSlot::score)
                .thenComparing(scoredSlot -> scoredSlot.slot().timestamp()));
        return scored;
    }

    private static List<ChargingSlot> allocateSlots(
            List<ScoredCandidateSlot> scoredCandidates,
            int maxStepsPerSlot,
            int totalSteps
    ) {
        int remainingSteps = totalSteps;
        List<ChargingSlot> slots = new ArrayList<>();

        for (ScoredCandidateSlot scored : scoredCandidates) {
            if (remainingSteps <= 0) {
                break;
            }

            int steps = Math.min(maxStepsPerSlot, remainingSteps);
            double powerDraw = steps * STEP_SIZE_KWH;
            if (powerDraw <= 0.0) {
                continue;
            }

            CandidateSlot slot = scored.slot();
            slots.add(new ChargingSlot(
                    slot.timestamp(),
                    powerDraw,
                    slot.price(),
                    slot.co2()
            ));
            remainingSteps -= steps;
        }

        return slots;
    }

    private static double calculateTotalCost(List<ChargingSlot> slots) {
        double cost = 0.0;
        for (ChargingSlot slot : slots) {
            cost += slot.powerDraw() * slot.currentPrice();
        }
        return cost;
    }

    private static double calculateTotalEmissions(List<ChargingSlot> slots) {
        double emissions = 0.0;
        for (ChargingSlot slot : slots) {
            emissions += slot.powerDraw() * slot.currentCO2();
        }
        return emissions;
    }

    private record CandidateSlot(LocalDateTime timestamp, double price, double co2) {
    }

    private record ScoredCandidateSlot(CandidateSlot slot, double score) {
    }
}
