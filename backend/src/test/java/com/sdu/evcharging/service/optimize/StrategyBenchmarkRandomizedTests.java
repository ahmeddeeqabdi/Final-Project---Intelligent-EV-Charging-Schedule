package com.sdu.evcharging.service.optimize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sdu.evcharging.domain.strategy.GridData;
import com.sdu.evcharging.domain.strategy.UserConstraints;
import com.sdu.evcharging.dto.schedule.ChargingSlot;
import com.sdu.evcharging.dto.schedule.ScheduleResult;

class StrategyBenchmarkRandomizedTests {

    private static final double STEP_SIZE_KWH = 0.5;
    private static final double EPS = 1e-9;
    private static final long SEED = 20260413L;
    private static final int SCENARIOS = 300;

    @Test
    void randomizedBenchmark_QuantifiesOptimalityGapAcrossScenarios() {
        DynamicProgrammingChargingStrategy optimal = new DynamicProgrammingChargingStrategy();
        GreedyChargingStrategy greedy = new GreedyChargingStrategy();
        Random random = new Random(SEED);

        List<Double> objectiveGapPercents = new ArrayList<>();
        List<Double> costGapPercents = new ArrayList<>();
        List<Double> emissionsGapPercents = new ArrayList<>();
        List<Double> optimalElectricityOnlyCosts = new ArrayList<>();
        List<Double> greedyElectricityOnlyCosts = new ArrayList<>();
        List<Double> optimalSwitchingEvents = new ArrayList<>();
        List<Double> greedySwitchingEvents = new ArrayList<>();
        List<Double> optimalEfficiencyLossCosts = new ArrayList<>();
        List<Double> greedyEfficiencyLossCosts = new ArrayList<>();
        List<Double> optimalRealWorldCosts = new ArrayList<>();
        List<Double> greedyRealWorldCosts = new ArrayList<>();
        List<Double> optimalLatenciesMs = new ArrayList<>();
        List<Double> greedyLatenciesMs = new ArrayList<>();

        for (int i = 0; i < SCENARIOS; i++) {
            Scenario scenario = generateFeasibleScenario(random, i);

            long startOptimal = System.nanoTime();
            ScheduleResult optimalResult = optimal.solve(scenario.constraints(), scenario.priceData(), scenario.co2Data());
            long endOptimal = System.nanoTime();
            optimalLatenciesMs.add((endOptimal - startOptimal) / 1_000_000.0);

            long startGreedy = System.nanoTime();
            ScheduleResult greedyResult = greedy.solve(scenario.constraints(), scenario.priceData(), scenario.co2Data());
            long endGreedy = System.nanoTime();
            greedyLatenciesMs.add((endGreedy - startGreedy) / 1_000_000.0);

            double optimalObjective = objectiveValue(
                    optimalResult.slots(),
                    scenario.priceData(),
                    scenario.co2Data(),
                    scenario.constraints().weightPrice(),
                    scenario.constraints().weightCO2(),
                    scenario.constraints().plugInTime(),
                    scenario.constraints().departureTime());

            double greedyObjective = objectiveValue(
                    greedyResult.slots(),
                    scenario.priceData(),
                    scenario.co2Data(),
                    scenario.constraints().weightPrice(),
                    scenario.constraints().weightCO2(),
                    scenario.constraints().plugInTime(),
                    scenario.constraints().departureTime());

            DynamicProgrammingChargingStrategy.RealWorldCostBreakdown optimalBreakdown =
                    DynamicProgrammingChargingStrategy.calculateRealWorldCost(
                            optimalResult.slots(),
                            scenario.constraints(),
                            scenario.priceData(),
                            scenario.co2Data());
            DynamicProgrammingChargingStrategy.RealWorldCostBreakdown greedyBreakdown =
                    DynamicProgrammingChargingStrategy.calculateRealWorldCost(
                            greedyResult.slots(),
                            scenario.constraints(),
                            scenario.priceData(),
                            scenario.co2Data());

            // DP must be no worse than greedy under the real-world cost model.
            assertTrue(optimalBreakdown.totalRealWorldCost() <= greedyBreakdown.totalRealWorldCost() + 1e-7,
                    "Scenario " + i + ": optimal real-world cost > greedy real-world cost");

            objectiveGapPercents.add(percentGap(greedyObjective, optimalObjective));
            costGapPercents.add(percentGap(greedyBreakdown.totalRealWorldCost(), optimalBreakdown.totalRealWorldCost()));
            emissionsGapPercents.add(percentGap(greedyResult.totalPredictedEmissions(), optimalResult.totalPredictedEmissions()));
            optimalElectricityOnlyCosts.add(optimalBreakdown.electricityOnlyCost());
            greedyElectricityOnlyCosts.add(greedyBreakdown.electricityOnlyCost());
            optimalSwitchingEvents.add((double) optimalBreakdown.switchingEvents());
            greedySwitchingEvents.add((double) greedyBreakdown.switchingEvents());
            optimalEfficiencyLossCosts.add(optimalBreakdown.efficiencyLossCost());
            greedyEfficiencyLossCosts.add(greedyBreakdown.efficiencyLossCost());
            optimalRealWorldCosts.add(optimalBreakdown.totalRealWorldCost());
            greedyRealWorldCosts.add(greedyBreakdown.totalRealWorldCost());
        }

        BenchmarkSummary objectiveSummary = BenchmarkSummary.of(objectiveGapPercents);
        BenchmarkSummary costSummary = BenchmarkSummary.of(costGapPercents);
        BenchmarkSummary emissionsSummary = BenchmarkSummary.of(emissionsGapPercents);
        BenchmarkSummary optimalElectricitySummary = BenchmarkSummary.of(optimalElectricityOnlyCosts);
        BenchmarkSummary greedyElectricitySummary = BenchmarkSummary.of(greedyElectricityOnlyCosts);
        BenchmarkSummary optimalSwitchingSummary = BenchmarkSummary.of(optimalSwitchingEvents);
        BenchmarkSummary greedySwitchingSummary = BenchmarkSummary.of(greedySwitchingEvents);
        BenchmarkSummary optimalEfficiencyLossSummary = BenchmarkSummary.of(optimalEfficiencyLossCosts);
        BenchmarkSummary greedyEfficiencyLossSummary = BenchmarkSummary.of(greedyEfficiencyLossCosts);
        BenchmarkSummary optimalRealWorldSummary = BenchmarkSummary.of(optimalRealWorldCosts);
        BenchmarkSummary greedyRealWorldSummary = BenchmarkSummary.of(greedyRealWorldCosts);
        BenchmarkSummary optimalLatencySummary = BenchmarkSummary.of(optimalLatenciesMs);
        BenchmarkSummary greedyLatencySummary = BenchmarkSummary.of(greedyLatenciesMs);

        System.out.printf(
                "\nRandomized benchmark (seed=%d, scenarios=%d)\n"
                        + "Objective gap %% (greedy - optimal): mean=%.4f p50=%.4f p95=%.4f max=%.4f\n"
                        + "Total real-cost gap %% (g-o):         mean=%.4f p50=%.4f p95=%.4f max=%.4f\n"
                        + "CO2 gap %% (greedy - optimal):       mean=%.4f p50=%.4f p95=%.4f max=%.4f\n"
                        + "\nAlgorithmic Latency (ms)\n"
                        + "Optimal (DP): mean=%.2f p50=%.2f p95=%.2f max=%.2f\n"
                        + "Greedy:       mean=%.2f p50=%.2f p95=%.2f max=%.2f\n"
                        + "\nPlan Quality (mean values)\n"
                        + "Electricity-only cost: optimal=%.4f greedy=%.4f\n"
                        + "Switching events:      optimal=%.4f greedy=%.4f\n"
                        + "Efficiency loss cost:  optimal=%.4f greedy=%.4f\n"
                        + "Total real-world cost: optimal=%.4f greedy=%.4f\n"
                        + "\nPlan Quality (p95)\n"
                        + "Electricity-only cost: optimal=%.4f greedy=%.4f\n"
                        + "Switching events:      optimal=%.4f greedy=%.4f\n"
                        + "Efficiency loss cost:  optimal=%.4f greedy=%.4f\n"
                        + "Total real-world cost: optimal=%.4f greedy=%.4f\n",
                SEED,
                SCENARIOS,
                objectiveSummary.mean(), objectiveSummary.p50(), objectiveSummary.p95(), objectiveSummary.max(),
                costSummary.mean(), costSummary.p50(), costSummary.p95(), costSummary.max(),
                emissionsSummary.mean(), emissionsSummary.p50(), emissionsSummary.p95(), emissionsSummary.max(),
                optimalLatencySummary.mean(), optimalLatencySummary.p50(), optimalLatencySummary.p95(), optimalLatencySummary.max(),
                greedyLatencySummary.mean(), greedyLatencySummary.p50(), greedyLatencySummary.p95(), greedyLatencySummary.max(),
                optimalElectricitySummary.mean(), greedyElectricitySummary.mean(),
                optimalSwitchingSummary.mean(), greedySwitchingSummary.mean(),
                optimalEfficiencyLossSummary.mean(), greedyEfficiencyLossSummary.mean(),
                optimalRealWorldSummary.mean(), greedyRealWorldSummary.mean(),
                optimalElectricitySummary.p95(), greedyElectricitySummary.p95(),
                optimalSwitchingSummary.p95(), greedySwitchingSummary.p95(),
                optimalEfficiencyLossSummary.p95(), greedyEfficiencyLossSummary.p95(),
                optimalRealWorldSummary.p95(), greedyRealWorldSummary.p95());
    }

    private static Scenario generateFeasibleScenario(Random random, int index) {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0).plusHours(index * 36L);

        while (true) {
            int slots = 4 + random.nextInt(21); // [4,24]
            LocalDateTime departure = start.plusHours(slots);

            double[] maxPowerOptions = {3.7, 7.4, 11.0, 22.0};
            double maxPower = maxPowerOptions[random.nextInt(maxPowerOptions.length)];

            double battery = 40.0 + random.nextDouble() * 60.0; // [40,100]
            double currentSoc = 5.0 + random.nextDouble() * 65.0; // [5,70]
            double targetSoc = Math.min(100.0, currentSoc + 10.0 + random.nextDouble() * 75.0);

            double weightPrice = random.nextDouble();
            double weightCo2 = random.nextDouble();
            if (weightPrice + weightCo2 < 1e-6) {
                weightPrice = 1.0;
            }

            UserConstraints constraints = new UserConstraints(
                    currentSoc,
                    targetSoc,
                    battery,
                    maxPower,
                    start,
                    departure,
                    random.nextBoolean() ? "DK1" : "DK2",
                    weightPrice,
                    weightCo2);

            List<GridData> priceData = new ArrayList<>(slots);
            List<GridData> co2Data = new ArrayList<>(slots);

            double priceBase = 0.10 + random.nextDouble() * 0.35;
            double co2Base = 80.0 + random.nextDouble() * 320.0;

            for (int h = 0; h < slots; h++) {
                LocalDateTime ts = start.plusHours(h);
                double dailyShape = 0.5 + 0.5 * Math.sin((2.0 * Math.PI * h) / 24.0);
                double price = Math.max(0.02, priceBase + (dailyShape * 0.18) + (random.nextDouble() - 0.5) * 0.10);
                double co2 = Math.max(20.0, co2Base + ((1.0 - dailyShape) * 140.0) + (random.nextDouble() - 0.5) * 120.0);
                priceData.add(new GridData(ts, price));
                co2Data.add(new GridData(ts, co2));
            }

            int maxStepsPerSlot = (int) Math.floor((maxPower / STEP_SIZE_KWH) + 1e-9);
            int totalSteps = (int) Math.ceil((constraints.energyRequiredKwh() / STEP_SIZE_KWH) - 1e-9);
            if (totalSteps <= 0) {
                continue;
            }
            if ((long) maxStepsPerSlot * slots >= totalSteps) {
                return new Scenario(constraints, priceData, co2Data);
            }
        }
    }

    private static double objectiveValue(
            List<ChargingSlot> slots,
            List<GridData> priceData,
            List<GridData> co2Data,
            double weightPriceRaw,
            double weightCo2Raw,
            LocalDateTime start,
            LocalDateTime end
    ) {
        double wp = Math.max(0.0, weightPriceRaw);
        double wc = Math.max(0.0, weightCo2Raw);
        double sum = wp + wc;
        if (sum <= 0.0) {
            wp = 0.5;
            wc = 0.5;
        } else {
            wp /= sum;
            wc /= sum;
        }

        List<GridData> inWindowPrice = priceData.stream()
                .filter(d -> !d.timestamp().isBefore(start) && d.timestamp().isBefore(end))
                .sorted(Comparator.comparing(GridData::timestamp))
                .toList();

        Map<LocalDateTime, Double> co2ByHour = new HashMap<>();
        for (GridData d : co2Data) {
            co2ByHour.put(d.timestamp(), d.value());
        }

        List<Double> prices = new ArrayList<>(inWindowPrice.size());
        List<Double> co2 = new ArrayList<>(inWindowPrice.size());
        for (GridData p : inWindowPrice) {
            prices.add(p.value());
            co2.add(co2ByHour.getOrDefault(p.timestamp(), 0.0));
        }

        List<Double> normalizedPrices = NormalizationUtility.minMaxNormalize(prices);
        List<Double> normalizedCo2 = NormalizationUtility.minMaxNormalize(co2);

        Map<LocalDateTime, Double> scoreByTime = new HashMap<>();
        for (int i = 0; i < inWindowPrice.size(); i++) {
            double score = wp * normalizedPrices.get(i) + wc * normalizedCo2.get(i);
            scoreByTime.put(inWindowPrice.get(i).timestamp(), score);
        }

        double objective = 0.0;
        for (ChargingSlot slot : slots) {
            objective += slot.powerDraw() * scoreByTime.getOrDefault(slot.timestamp(), 0.0);
        }
        return objective;
    }

    // Calculates percentage savings of target relative to baseline: (baselineValue - targetValue) / |baselineValue| * 100
    private static double percentGap(double baselineValue, double targetValue) {
        double denom = Math.max(Math.abs(baselineValue), EPS);
        return ((baselineValue - targetValue) / denom) * 100.0;
    }

    private record Scenario(UserConstraints constraints, List<GridData> priceData, List<GridData> co2Data) {
    }

    private record BenchmarkSummary(double mean, double p50, double p95, double max) {
        private static BenchmarkSummary of(List<Double> values) {
            List<Double> sorted = values.stream().sorted().toList();
            double mean = sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double p50 = percentile(sorted, 0.50);
            double p95 = percentile(sorted, 0.95);
            double max = sorted.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            return new BenchmarkSummary(mean, p50, p95, max);
        }

        private static double percentile(List<Double> sorted, double q) {
            if (sorted.isEmpty()) {
                return 0.0;
            }
            int idx = (int) Math.floor((sorted.size() - 1) * q);
            return sorted.get(idx);
        }
    }
}
