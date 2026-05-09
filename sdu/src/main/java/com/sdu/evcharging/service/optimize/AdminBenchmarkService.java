package com.sdu.evcharging.service.optimize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.stereotype.Service;

import com.sdu.evcharging.domain.strategy.ChargingStrategy;
import com.sdu.evcharging.domain.strategy.GridData;
import com.sdu.evcharging.domain.strategy.UserConstraints;
import com.sdu.evcharging.dto.admin.AdminBenchmarkResponse;
import com.sdu.evcharging.dto.schedule.ChargingSlot;
import com.sdu.evcharging.dto.schedule.ScheduleResult;

@Service
public class AdminBenchmarkService {

    private static final double STEP_SIZE_KWH = 0.5;
    private static final double EPS = 1e-9;
    private static final int DEFAULT_SCENARIOS = 300;
    private static final int MAX_SCENARIOS = 2_000;
    private static final long DEFAULT_SEED = 20260413L;

    private final ChargingStrategy optimalStrategy;
    private final ChargingStrategy greedyStrategy;
    private final ChargingStrategy mipStrategy;
    private final ChargingStrategy naiveStrategy;

    public AdminBenchmarkService(
            DynamicProgrammingChargingStrategy optimalStrategy,
            GreedyChargingStrategy greedyStrategy,
            MipChargingStrategy mipStrategy,
            NaiveChargingStrategy naiveStrategy
    ) {
        this.optimalStrategy = optimalStrategy;
        this.greedyStrategy = greedyStrategy;
        this.mipStrategy = mipStrategy;
        this.naiveStrategy = naiveStrategy;
    }

    public AdminBenchmarkResponse run(Integer scenariosInput, Long seedInput) {
        int scenarios = normalizeScenarios(scenariosInput);
        long seed = seedInput == null ? DEFAULT_SEED : seedInput;
        Random random = new Random(seed);

        List<Double> objectiveGapPercents = new ArrayList<>();
        List<Double> costGapPercents = new ArrayList<>();
        List<Double> emissionsGapPercents = new ArrayList<>();
        List<Double> optimalRuntimeMs = new ArrayList<>();
        List<Double> greedyRuntimeMs = new ArrayList<>();
        List<Double> mipRuntimeMs = new ArrayList<>();
        List<Double> naiveRuntimeMs = new ArrayList<>();

        // 1. Warm-up Phase: Run all 50 times to force JIT and native lib loading
        for (int i = 0; i < 50; i++) {
            Scenario scenario = generateFeasibleScenario(random, i);
            optimalStrategy.solve(scenario.constraints(), scenario.priceData(), scenario.co2Data());
            greedyStrategy.solve(scenario.constraints(), scenario.priceData(), scenario.co2Data());
            mipStrategy.solve(scenario.constraints(), scenario.priceData(), scenario.co2Data());
            naiveStrategy.solve(scenario.constraints(), scenario.priceData(), scenario.co2Data());
        }

        // 2. Measurement Phase
        for (int i = 0; i < scenarios; i++) {
            Scenario scenario = generateFeasibleScenario(random, i);

            long optimalStart = System.nanoTime();
            ScheduleResult optimalResult = optimalStrategy.solve(
                    scenario.constraints(), scenario.priceData(), scenario.co2Data());
            double optimalMs = nanosToMs(System.nanoTime() - optimalStart);

            long greedyStart = System.nanoTime();
            ScheduleResult greedyResult = greedyStrategy.solve(
                    scenario.constraints(), scenario.priceData(), scenario.co2Data());
            double greedyMs = nanosToMs(System.nanoTime() - greedyStart);

            long mipStart = System.nanoTime();
            ScheduleResult mipResult = mipStrategy.solve(
                    scenario.constraints(), scenario.priceData(), scenario.co2Data());
            double mipMs = nanosToMs(System.nanoTime() - mipStart);

            long naiveStart = System.nanoTime();
            ScheduleResult naiveResult = naiveStrategy.solve(
                    scenario.constraints(), scenario.priceData(), scenario.co2Data());
            double naiveMs = nanosToMs(System.nanoTime() - naiveStart);

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

                DynamicProgrammingChargingStrategy.RealWorldCostBreakdown optimalRealCost =
                    DynamicProgrammingChargingStrategy.calculateRealWorldCost(
                        optimalResult.slots(),
                        scenario.constraints(),
                        scenario.priceData(),
                        scenario.co2Data());
                DynamicProgrammingChargingStrategy.RealWorldCostBreakdown greedyRealCost =
                    DynamicProgrammingChargingStrategy.calculateRealWorldCost(
                        greedyResult.slots(),
                        scenario.constraints(),
                        scenario.priceData(),
                        scenario.co2Data());

            objectiveGapPercents.add(percentGap(greedyObjective, optimalObjective));
            costGapPercents.add(percentGap(greedyRealCost.totalRealWorldCost(), optimalRealCost.totalRealWorldCost()));
            emissionsGapPercents.add(percentGap(greedyResult.totalPredictedEmissions(), optimalResult.totalPredictedEmissions()));
            optimalRuntimeMs.add(optimalMs);
            greedyRuntimeMs.add(greedyMs);
            mipRuntimeMs.add(mipMs);
            naiveRuntimeMs.add(naiveMs);
        }

        MetricSummary objectiveSummary = MetricSummary.of(objectiveGapPercents);
        MetricSummary costSummary = MetricSummary.of(costGapPercents);
        MetricSummary emissionsSummary = MetricSummary.of(emissionsGapPercents);
        
        MetricSummary optimalRuntimeSummary = MetricSummary.of(optimalRuntimeMs);
        MetricSummary greedyRuntimeSummary = MetricSummary.of(greedyRuntimeMs);
        MetricSummary mipRuntimeSummary = MetricSummary.of(mipRuntimeMs);
        MetricSummary naiveRuntimeSummary = MetricSummary.of(naiveRuntimeMs);

        return new AdminBenchmarkResponse(
                scenarios,
                seed,
                new AdminBenchmarkResponse.MetricSummary(
                        objectiveSummary.mean(), objectiveSummary.p50(), objectiveSummary.p95(), objectiveSummary.max()),
                new AdminBenchmarkResponse.MetricSummary(
                        costSummary.mean(), costSummary.p50(), costSummary.p95(), costSummary.max()),
                new AdminBenchmarkResponse.MetricSummary(
                        emissionsSummary.mean(), emissionsSummary.p50(), emissionsSummary.p95(), emissionsSummary.max()),
                new AdminBenchmarkResponse.RuntimeSummary(
                        optimalRuntimeSummary.mean(), optimalRuntimeSummary.p50(), optimalRuntimeSummary.p95(), optimalRuntimeSummary.max(),
                        greedyRuntimeSummary.mean(), greedyRuntimeSummary.p50(), greedyRuntimeSummary.p95(), greedyRuntimeSummary.max(),
                        mipRuntimeSummary.mean(), mipRuntimeSummary.p50(), mipRuntimeSummary.p95(), mipRuntimeSummary.max(),
                        naiveRuntimeSummary.mean(), naiveRuntimeSummary.p50(), naiveRuntimeSummary.p95(), naiveRuntimeSummary.max(),
                        optimalRuntimeSummary.mean() - greedyRuntimeSummary.mean()
                )
        );
    }

    private static int normalizeScenarios(Integer scenariosInput) {
        if (scenariosInput == null) {
            return DEFAULT_SCENARIOS;
        }
        if (scenariosInput <= 0) {
            throw new IllegalArgumentException("scenarios must be positive");
        }
        return Math.min(scenariosInput, MAX_SCENARIOS);
    }

    private static Scenario generateFeasibleScenario(Random random, int index) {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0).plusHours(index * 36L);

        while (true) {
            // Generate between 20 and 500 slots to simulate high-frequency (5-min) dispatch scaling 
            // or multi-day scenarios, legitimately testing the combinatorial limits of the MIP solver.
            int slots = 20 + random.nextInt(481);
            LocalDateTime departure = start.plusHours(slots);

            double[] maxPowerOptions = {3.7, 7.4, 11.0, 22.0};
            double maxPower = maxPowerOptions[random.nextInt(maxPowerOptions.length)];

            double battery = 40.0 + random.nextDouble() * 60.0;
            double currentSoc = 5.0 + random.nextDouble() * 65.0;
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
                    weightCo2
            );

            List<GridData> priceData = new ArrayList<>(slots);
            List<GridData> co2Data = new ArrayList<>(slots);

            double priceBase = 0.10 + random.nextDouble() * 0.35;
            double co2Base = 80.0 + random.nextDouble() * 320.0;

            for (int h = 0; h < slots; h++) {
                // 15-minute resolution
                LocalDateTime ts = start.plusMinutes(h * 15L);
                double dailyShape = 0.5 + 0.5 * Math.sin((2.0 * Math.PI * (h / 4.0)) / 24.0);
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

    private static double percentGap(double candidate, double baseline) {
        double denom = Math.max(Math.abs(baseline), EPS);
        return ((candidate - baseline) / denom) * 100.0;
    }

    private static double nanosToMs(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private record Scenario(UserConstraints constraints, List<GridData> priceData, List<GridData> co2Data) {
    }

    private record MetricSummary(double mean, double p50, double p95, double max) {
        private static MetricSummary of(List<Double> values) {
            List<Double> sorted = values.stream().sorted().toList();
            double mean = sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double p50 = percentile(sorted, 0.50);
            double p95 = percentile(sorted, 0.95);
            double max = sorted.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            return new MetricSummary(mean, p50, p95, max);
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
