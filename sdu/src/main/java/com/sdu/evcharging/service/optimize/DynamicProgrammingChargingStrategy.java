package com.sdu.evcharging.service.optimize;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
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
public class DynamicProgrammingChargingStrategy implements ChargingStrategy {

    private static final double ENERGY_TOLERANCE = 1e-3;
    private static final double STEP_ROUNDING_EPSILON = 1e-9;
    private static final double DP_EPSILON = 1e-12;
    private static final double DEFAULT_WEIGHT = 0.5;
    private static final double DEFAULT_CO2 = 0.0;
    private static final double SLOT_DURATION_HOURS = 1.0;
    private static final double STEP_SIZE_KWH = 0.5;
    private static final double STARTUP_PENALTY = 0.25;
    private static final double EFFICIENCY_MAX = 0.99;
    private static final double EFFICIENCY_MIN = 0.85;
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

        int totalSteps = toRoundedSteps(energyRequiredKwh);
        double targetEnergyKwh = totalSteps * STEP_SIZE_KWH;
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

        List<ChargingSlot> slots = solveWithDynamicProgramming(
            weightedCandidates,
            totalSteps,
            maxStepsPerSlot,
            constraints.batteryCapacityKwh());
        slots.sort(Comparator.comparing(ChargingSlot::timestamp));

        double scheduledEnergy = slots.stream().mapToDouble(ChargingSlot::powerDraw).sum();
        if (Math.abs(scheduledEnergy - targetEnergyKwh) > ENERGY_TOLERANCE) {
            throw new IllegalStateException("DP backtracking error: scheduled energy does not match required energy.");
        }

        double totalCost = calculateTotalCost(slots);
        double totalEmissions = calculateTotalEmissions(slots);
        return new ScheduleResult(slots, totalCost, totalEmissions);
    }

    private static ScheduleResult emptyResult() {
        return new ScheduleResult(List.of(), 0.0, 0.0);
    }

    private static int toRoundedSteps(double energyRequiredKwh) {
        double rawSteps = energyRequiredKwh / STEP_SIZE_KWH;
        return (int) Math.max(1, Math.ceil(rawSteps - STEP_ROUNDING_EPSILON));
    }

    private static int toMaxStepsPerSlot(double maxChargingPowerKw) {
        double maxEnergyPerSlot = maxChargingPowerKw * SLOT_DURATION_HOURS;
        return (int) Math.floor((maxEnergyPerSlot / STEP_SIZE_KWH) + 1e-9);
    }

    private static void guardStateSpace(int numSlots, int totalSteps) {
        long states = (long) (numSlots + 1) * (totalSteps + 1) * 2L;
        if (states > MAX_DP_STATES) {
            throw new IllegalArgumentException(
                    "DP state space too large (" + states + " states). "
                            + "Reduce the charging window/energy target or use greedy."
            );
        }
        log.info("[DynamicProgrammingChargingStrategy] Solving DP with {} slots and {} energy steps ({} states)",
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
            int maxStepsPerSlot,
            double maxCapacityKwh
    ) {
        int numSlots = weightedCandidates.size();
        double[][][] dp = new double[numSlots + 1][totalSteps + 1][2];
        int[][][] chosenK = new int[numSlots + 1][totalSteps + 1][2];
        int[][][] prevState = new int[numSlots + 1][totalSteps + 1][2];

        for (int t = 0; t <= numSlots; t++) {
            for (int e = 0; e <= totalSteps; e++) {
                Arrays.fill(dp[t][e], INF);
                Arrays.fill(chosenK[t][e], -1);
                Arrays.fill(prevState[t][e], -1);
            }
        }
        dp[0][0][0] = 0.0;
        chosenK[0][0][0] = 0;
        prevState[0][0][0] = 0;

        for (int t = 1; t <= numSlots; t++) {
            WeightedCandidateSlot slot = weightedCandidates.get(t - 1);
            for (int e = 0; e <= totalSteps; e++) {
                for (int previousCharging = 0; previousCharging <= 1; previousCharging++) {
                    int maxK = Math.min(e, maxStepsPerSlot);
                    for (int k = 0; k <= maxK; k++) {
                        int ePrev = e - k;
                        double previousCost = dp[t - 1][ePrev][previousCharging];
                        if (Double.isInfinite(previousCost)) {
                            continue;
                        }

                        double energyAddedKwh = k * STEP_SIZE_KWH;
                        double soc = maxCapacityKwh <= ENERGY_TOLERANCE
                                ? 0.0
                                : clamp01((ePrev * STEP_SIZE_KWH) / maxCapacityKwh);
                        double efficiency = socToEfficiency(soc);
                        double transitionCost = previousCost
                                + ((slot.weightedScore() * energyAddedKwh * SLOT_DURATION_HOURS) / efficiency);
                        if (k > 0 && previousCharging == 0) {
                            transitionCost += STARTUP_PENALTY;
                        }

                        int chargingState = k > 0 ? 1 : 0;
                        double currentBest = dp[t][e][chargingState];
                        int currentBestK = chosenK[t][e][chargingState];
                        if (transitionCost < currentBest - DP_EPSILON
                                || (Math.abs(transitionCost - currentBest) <= DP_EPSILON
                                && (currentBestK < 0 || k < currentBestK))) {
                            dp[t][e][chargingState] = transitionCost;
                            chosenK[t][e][chargingState] = k;
                            prevState[t][e][chargingState] = previousCharging;
                        }
                    }
                }
            }
        }

        int finalChargingState = dp[numSlots][totalSteps][0] <= dp[numSlots][totalSteps][1] ? 0 : 1;
        if (Double.isInfinite(dp[numSlots][totalSteps][finalChargingState])) {
            throw new IllegalArgumentException("No feasible DP schedule found for required energy within constraints.");
        }

        List<ChargingSlot> slots = new ArrayList<>();
        int remainingSteps = totalSteps;
        int currentChargingState = finalChargingState;

        for (int t = numSlots; t >= 1; t--) {
            int chosenSteps = chosenK[t][remainingSteps][currentChargingState];
            if (chosenSteps < 0) {
                throw new IllegalStateException("DP backtracking failed due to invalid decision state.");
            }

            int priorChargingState = prevState[t][remainingSteps][currentChargingState];
            if (priorChargingState < 0) {
                throw new IllegalStateException("DP backtracking failed due to missing prior charging state.");
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
            currentChargingState = priorChargingState;
        }

        if (remainingSteps != 0) {
            throw new IllegalStateException("DP backtracking did not reconstruct the required energy state.");
        }

        return slots;
    }

    public static RealWorldCostBreakdown calculateRealWorldCost(
            List<ChargingSlot> slots,
            UserConstraints constraints,
            List<GridData> priceData,
            List<GridData> co2Data
    ) {
        Weight normalizedWeights = normalizeWeights(constraints.weightPrice(), constraints.weightCO2());
        Map<LocalDateTime, Double> scoreByTime = buildScoreByTime(
                priceData,
                co2Data,
                constraints.plugInTime(),
                constraints.departureTime(),
                normalizedWeights);

        List<LocalDateTime> orderedTimestamps = scoreByTime.keySet().stream().sorted().toList();
        Map<LocalDateTime, Double> energyByTime = new HashMap<>();
        for (ChargingSlot slot : slots) {
            energyByTime.merge(slot.timestamp(), slot.powerDraw(), Double::sum);
        }

        double electricityOnlyCost = 0.0;
        double efficiencyLossCost = 0.0;
        double wastedEnergyKwh = 0.0;
        int switchingEvents = 0;
        double deliveredEnergyKwh = 0.0;
        boolean previouslyCharging = false;

        for (LocalDateTime timestamp : orderedTimestamps) {
            double energyAddedKwh = energyByTime.getOrDefault(timestamp, 0.0);
            boolean chargingNow = energyAddedKwh > ENERGY_TOLERANCE;

            if (chargingNow) {
                double score = scoreByTime.getOrDefault(timestamp, 0.0);
                double soc = constraints.batteryCapacityKwh() <= ENERGY_TOLERANCE
                        ? 0.0
                        : clamp01(deliveredEnergyKwh / constraints.batteryCapacityKwh());
                double efficiency = socToEfficiency(soc);

                double baseCost = score * energyAddedKwh * SLOT_DURATION_HOURS;
                double adjustedCost = baseCost / efficiency;
                double wasted = energyAddedKwh * ((1.0 / efficiency) - 1.0);

                electricityOnlyCost += baseCost;
                efficiencyLossCost += (adjustedCost - baseCost);
                wastedEnergyKwh += wasted;

                if (!previouslyCharging) {
                    switchingEvents++;
                }
                deliveredEnergyKwh += energyAddedKwh;
            }

            previouslyCharging = chargingNow;
        }

        double switchingPenaltyCost = switchingEvents * STARTUP_PENALTY;
        double totalRealWorldCost = electricityOnlyCost + efficiencyLossCost + switchingPenaltyCost;

        return new RealWorldCostBreakdown(
                electricityOnlyCost,
                switchingEvents,
                switchingPenaltyCost,
                efficiencyLossCost,
                wastedEnergyKwh,
                totalRealWorldCost);
    }

    private static Map<LocalDateTime, Double> buildScoreByTime(
            List<GridData> priceData,
            List<GridData> co2Data,
            LocalDateTime start,
            LocalDateTime end,
            Weight weight
    ) {
        List<GridData> inWindowPrice = priceData.stream()
                .filter(d -> !d.timestamp().isBefore(start) && d.timestamp().isBefore(end))
                .sorted(Comparator.comparing(GridData::timestamp))
                .toList();

        Map<LocalDateTime, Double> co2ByHour = new HashMap<>();
        for (GridData d : co2Data) {
            co2ByHour.put(d.timestamp(), d.value());
        }

        List<Double> prices = new ArrayList<>(inWindowPrice.size());
        List<Double> co2Values = new ArrayList<>(inWindowPrice.size());
        for (GridData p : inWindowPrice) {
            prices.add(p.value());
            co2Values.add(co2ByHour.getOrDefault(p.timestamp(), 0.0));
        }

        List<Double> normalizedPrices = NormalizationUtility.minMaxNormalize(prices);
        List<Double> normalizedCo2 = NormalizationUtility.minMaxNormalize(co2Values);

        Map<LocalDateTime, Double> scoreByTime = new HashMap<>();
        for (int i = 0; i < inWindowPrice.size(); i++) {
            double score = (weight.priceWeight() * normalizedPrices.get(i))
                    + (weight.co2Weight() * normalizedCo2.get(i));
            scoreByTime.put(inWindowPrice.get(i).timestamp(), score);
        }
        return scoreByTime;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double socToEfficiency(double soc) {
        double eff = EFFICIENCY_MAX - (soc * (EFFICIENCY_MAX - EFFICIENCY_MIN));
        return Math.max(EFFICIENCY_MIN, Math.min(EFFICIENCY_MAX, eff));
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

    public record RealWorldCostBreakdown(
            double electricityOnlyCost,
            int switchingEvents,
            double switchingPenaltyCost,
            double efficiencyLossCost,
            double wastedEnergyKwh,
            double totalRealWorldCost
    ) {
    }
}