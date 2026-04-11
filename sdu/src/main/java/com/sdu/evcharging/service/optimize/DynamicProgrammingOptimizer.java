package com.sdu.evcharging.service.optimize;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sdu.evcharging.domain.strategy.ChargingStrategy;
import com.sdu.evcharging.domain.strategy.GridData;
import com.sdu.evcharging.domain.strategy.UserConstraints;
import com.sdu.evcharging.dto.schedule.ChargingSlot;
import com.sdu.evcharging.dto.schedule.ScheduleResult;

import lombok.extern.slf4j.Slf4j;

@Component("optimal")
@Slf4j
public class DynamicProgrammingOptimizer implements ChargingStrategy {

    private static final double ENERGY_TOLERANCE = 1e-3;
    private static final double REPRESENTABILITY_TOLERANCE = 1e-9;
    private static final double DP_EPSILON = 1e-12;
    private static final double DEFAULT_WEIGHT = 0.5;
    private static final double DEFAULT_CO2 = 0.0;
    private static final double SLOT_DURATION_HOURS = 1.0;
    private static final double STEP_SIZE_KWH = 0.5;
    private static final long MAX_DP_STATES = 3_000_000L;
    private static final double INF = Double.POSITIVE_INFINITY;

    @Override
    public ScheduleResult solve(UserConstraints constraints, List<GridData> priceData, List<GridData> co2Data) {
        Objects.requireNonNull(constraints, "constraints must not be null");

        if (priceData == null || priceData.isEmpty()) {
            return emptyResult();
        }

        double energyRequiredKwh = constraints.energyRequiredKwh();
        if (energyRequiredKwh <= ENERGY_TOLERANCE) {
            return emptyResult();
        }

        int totalSteps = toRepresentableSteps(energyRequiredKwh);
        int maxStepsPerSlot = toMaxStepsPerSlot(constraints.maxChargingPowerKw());
        if (maxStepsPerSlot <= 0) {
            throw new IllegalArgumentException("Max charging power is too low for the configured DP step size.");
        }

        Map<LocalDateTime, Double> co2ByTime = buildCo2Lookup(co2Data);
        double defaultCo2 = averageCo2OrDefault(co2Data);
        List<CandidateSlot> candidates = buildCandidates(constraints, priceData, co2ByTime, defaultCo2);
        if (candidates.isEmpty()) {
            return emptyResult();
        }

        long maxDeliverableSteps = (long) maxStepsPerSlot * candidates.size();
        if (maxDeliverableSteps < totalSteps) {
            throw new IllegalArgumentException("Charging window is infeasible for the required energy and max power.");
        }

        guardStateSpace(candidates.size(), totalSteps);

        Weight normalizedWeights = normalizeWeights(constraints.weightPrice(), constraints.weightCO2());
        List<WeightedCandidateSlot> weightedCandidates = scoreCandidates(candidates, normalizedWeights);

        List<ChargingSlot> slots = solveWithDynamicProgramming(weightedCandidates, totalSteps, maxStepsPerSlot);
        slots.sort(Comparator.comparing(ChargingSlot::timestamp));

        double scheduledEnergy = slots.stream().mapToDouble(ChargingSlot::powerDraw).sum();
        if (Math.abs(scheduledEnergy - energyRequiredKwh) > ENERGY_TOLERANCE) {
            throw new IllegalStateException("DP backtracking error: scheduled energy does not match required energy.");
        }

        double totalCost = calculateTotalCost(slots);
        double totalEmissions = calculateTotalEmissions(slots);
        return new ScheduleResult(slots, totalCost, totalEmissions);
    }

    private static ScheduleResult emptyResult() {
        return new ScheduleResult(List.of(), 0.0, 0.0);
    }

    private static int toRepresentableSteps(double energyRequiredKwh) {
        double rawSteps = energyRequiredKwh / STEP_SIZE_KWH;
        double rounded = Math.rint(rawSteps);
        if (Math.abs(rawSteps - rounded) > REPRESENTABILITY_TOLERANCE) {
            throw new IllegalArgumentException(String.format(
                    "Required energy %.6f kWh is not representable with step size %.2f kWh.",
                    energyRequiredKwh,
                    STEP_SIZE_KWH
            ));
        }
        return (int) rounded;
    }

    private static int toMaxStepsPerSlot(double maxChargingPowerKw) {
        double maxEnergyPerSlot = maxChargingPowerKw * SLOT_DURATION_HOURS;
        return (int) Math.floor((maxEnergyPerSlot / STEP_SIZE_KWH) + REPRESENTABILITY_TOLERANCE);
    }

    private static void guardStateSpace(int numSlots, int totalSteps) {
        long states = (long) (numSlots + 1) * (totalSteps + 1);
        if (states > MAX_DP_STATES) {
            throw new IllegalArgumentException(
                    "DP state space too large (" + states + " states). "
                            + "Reduce the charging window/energy target or use greedy."
            );
        }
        log.info("[DynamicProgrammingOptimizer] Solving DP with {} slots and {} energy steps ({} states)",
                numSlots, totalSteps, states);
    }

    private static Map<LocalDateTime, Double> buildCo2Lookup(List<GridData> co2Data) {
        if (co2Data == null || co2Data.isEmpty()) {
            return Map.of();
        }

        return co2Data.stream()
                .filter(Objects::nonNull)
                .filter(data -> data.timestamp() != null)
                .collect(Collectors.groupingBy(
                        data -> data.timestamp().truncatedTo(ChronoUnit.HOURS),
                        Collectors.averagingDouble(GridData::value)
                ));
    }

    private static double averageCo2OrDefault(List<GridData> co2Data) {
        if (co2Data == null || co2Data.isEmpty()) {
            return DEFAULT_CO2;
        }
        return co2Data.stream().mapToDouble(GridData::value).average().orElse(DEFAULT_CO2);
    }

    private static List<CandidateSlot> buildCandidates(
            UserConstraints constraints,
            List<GridData> priceData,
            Map<LocalDateTime, Double> co2ByTime,
            double defaultCo2
    ) {
        List<CandidateSlot> candidates = new ArrayList<>();
        for (GridData pricePoint : priceData) {
            if (pricePoint == null || pricePoint.timestamp() == null) {
                continue;
            }

            LocalDateTime timestamp = pricePoint.timestamp();
            if (!isWithinWindow(timestamp, constraints)) {
                continue;
            }

            LocalDateTime hourBucket = timestamp.truncatedTo(ChronoUnit.HOURS);
            double co2 = co2ByTime.getOrDefault(hourBucket, defaultCo2);
            candidates.add(new CandidateSlot(timestamp, pricePoint.value(), co2));
        }
        candidates.sort(Comparator.comparing(CandidateSlot::timestamp));
        return candidates;
    }

    private static boolean isWithinWindow(LocalDateTime timestamp, UserConstraints constraints) {
        return !timestamp.isBefore(constraints.plugInTime())
                && timestamp.isBefore(constraints.departureTime());
    }

    private static Weight normalizeWeights(double weightPriceInput, double weightCo2Input) {
        double weightPrice = Math.max(0.0, weightPriceInput);
        double weightCo2 = Math.max(0.0, weightCo2Input);
        double weightSum = weightPrice + weightCo2;
        if (weightSum <= 0.0) {
            return new Weight(DEFAULT_WEIGHT, DEFAULT_WEIGHT);
        }
        return new Weight(weightPrice / weightSum, weightCo2 / weightSum);
    }

    private static List<WeightedCandidateSlot> scoreCandidates(List<CandidateSlot> candidates, Weight weight) {
        List<Double> prices = candidates.stream().map(CandidateSlot::price).toList();
        List<Double> co2Values = candidates.stream().map(CandidateSlot::co2).toList();
        List<Double> normalizedPrices = NormalizationUtility.minMaxNormalize(prices);
        List<Double> normalizedCo2 = NormalizationUtility.minMaxNormalize(co2Values);

        List<WeightedCandidateSlot> weighted = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            double score = (weight.priceWeight() * normalizedPrices.get(i))
                    + (weight.co2Weight() * normalizedCo2.get(i));
            weighted.add(new WeightedCandidateSlot(candidates.get(i), score));
        }
        return weighted;
    }

    private static List<ChargingSlot> solveWithDynamicProgramming(
            List<WeightedCandidateSlot> weightedCandidates,
            int totalSteps,
            int maxStepsPerSlot
    ) {
        int numSlots = weightedCandidates.size();
        double[][] dp = new double[numSlots + 1][totalSteps + 1];
        int[][] chosenK = new int[numSlots + 1][totalSteps + 1];

        for (int t = 0; t <= numSlots; t++) {
            Arrays.fill(dp[t], INF);
            Arrays.fill(chosenK[t], -1);
        }
        dp[0][0] = 0.0;
        chosenK[0][0] = 0;

        for (int t = 1; t <= numSlots; t++) {
            WeightedCandidateSlot slot = weightedCandidates.get(t - 1);
            for (int e = 0; e <= totalSteps; e++) {
                int maxK = Math.min(e, maxStepsPerSlot);
                double bestCost = INF;
                int bestK = 0;

                for (int k = 0; k <= maxK; k++) {
                    double previousCost = dp[t - 1][e - k];
                    if (Double.isInfinite(previousCost)) {
                        continue;
                    }

                    double transitionCost = previousCost + (k * STEP_SIZE_KWH * slot.weightedScore() * SLOT_DURATION_HOURS);
                    if (transitionCost < bestCost - DP_EPSILON
                            || (Math.abs(transitionCost - bestCost) <= DP_EPSILON && k < bestK)) {
                        bestCost = transitionCost;
                        bestK = k;
                    }
                }

                dp[t][e] = bestCost;
                chosenK[t][e] = bestK;
            }
        }

        if (Double.isInfinite(dp[numSlots][totalSteps])) {
            throw new IllegalArgumentException("No feasible DP schedule found for required energy within constraints.");
        }

        List<ChargingSlot> slots = new ArrayList<>();
        int remainingSteps = totalSteps;

        for (int t = numSlots; t >= 1; t--) {
            int chosenSteps = chosenK[t][remainingSteps];
            if (chosenSteps < 0) {
                throw new IllegalStateException("DP backtracking failed due to invalid decision state.");
            }

            if (chosenSteps > 0) {
                WeightedCandidateSlot slot = weightedCandidates.get(t - 1);
                double powerDraw = chosenSteps * STEP_SIZE_KWH;
                slots.add(new ChargingSlot(
                        slot.slot().timestamp(),
                        powerDraw,
                        slot.slot().price(),
                        slot.slot().co2()
                ));
            }
            remainingSteps -= chosenSteps;
        }

        if (remainingSteps != 0) {
            throw new IllegalStateException("DP backtracking did not reconstruct the required energy state.");
        }

        return slots;
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

    private record CandidateSlot(LocalDateTime timestamp, double price, double co2) {
    }

    private record WeightedCandidateSlot(CandidateSlot slot, double weightedScore) {
    }

    private record Weight(double priceWeight, double co2Weight) {
    }
}