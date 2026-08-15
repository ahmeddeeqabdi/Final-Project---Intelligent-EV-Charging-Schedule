package com.sdu.evcharging.service.optimize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private static final long MAX_DP_STATES = 3_000_000L;
    private static final double INF = Double.POSITIVE_INFINITY;

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
        double targetEnergyKwh = totalSteps * STEP_SIZE_KWH;
        int maxStepsPerSlot = toMaxStepsPerSlot(constraints.maxChargingPowerKw());
        if (maxStepsPerSlot <= 0) {
            throw new IllegalArgumentException("Max charging power is too low for the configured DP step size.");
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

        guardStateSpace(candidates.size(), totalSteps);

        StrategySupport.Weight normalizedWeights = StrategySupport.normalizeWeights(
                constraints.weightPrice(),
                constraints.weightCO2(),
                DEFAULT_WEIGHT);
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

            LocalDateTime hourBucket = StrategySupport.toHourBucket(timestamp);
            double co2 = co2ByTime.getOrDefault(hourBucket, defaultCo2);
            candidates.add(new CandidateSlot(timestamp, pricePoint.value(), co2));
        }
        candidates.sort(Comparator.comparing(CandidateSlot::timestamp));
        return candidates;
    }

    private static boolean isWithinWindow(LocalDateTime timestamp, UserConstraints constraints) {
        return StrategySupport.isWithinWindow(timestamp, constraints);
    }

    private static List<WeightedCandidateSlot> scoreCandidates(
            List<CandidateSlot> candidates,
            StrategySupport.Weight weight
    ) {
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

                        double soc = maxCapacityKwh <= ENERGY_TOLERANCE
                                ? 0.0
                                : clamp01((ePrev * STEP_SIZE_KWH) / maxCapacityKwh);
                                
                        double energyAddedKwh = k * STEP_SIZE_KWH;
                        double baseMaxPower = (maxStepsPerSlot * STEP_SIZE_KWH) / SLOT_DURATION_HOURS;
                        int maxStepsAllowed = (int) (maxChargingPowerAtSoc(soc, baseMaxPower) / STEP_SIZE_KWH);
                        if (k > maxStepsAllowed) {
                            continue; // Exceeds CC/CV power taper limits
                        }

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
        StrategySupport.Weight normalizedWeights = StrategySupport.normalizeWeights(
            constraints.weightPrice(),
            constraints.weightCO2(),
            DEFAULT_WEIGHT);
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
            StrategySupport.Weight weight
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
        // CC/CV curve: 90% up to 80% SoC, then steep linear drop down to 70% at 100% SoC
        if (soc <= 0.80) {
            return 0.90;
        } else {
            return Math.max(0.70, 0.90 - (soc - 0.80));
        }
    }

    private static double maxChargingPowerAtSoc(double soc, double baseMaxPower) {
        // Power tapering curve (Constant Voltage phase limits power)
        if (soc <= 0.80) {
            return baseMaxPower; // Full power allowed in CC phase
        } else if (soc >= 0.98) {
            return Math.min(baseMaxPower, 2.0); // Extreme throttle at the very top (2kW max)
        } else {
            // Linear throttle from baseMaxPower down to 2.0kW between 80% and 98% SoC
            double throttleCurve = baseMaxPower - ((soc - 0.80) / 0.18) * (baseMaxPower - 2.0);
            return Math.min(baseMaxPower, Math.max(2.0, throttleCurve));
        }
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

    private record WeightedCandidateSlot(CandidateSlot slot, double weightedScore) {
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