package com.sdu.evcharging.service.optimize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
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

        Map<StrategyId, StrategyRunContext> contexts = new EnumMap<>(StrategyId.class);
        for (StrategyId strategyId : StrategyId.values()) {
            contexts.put(strategyId, newRunContext(strategyFor(strategyId)));
        }

        // 1. Warm-up Phase: Run all 50 times to force JIT and native lib loading
        for (int i = 0; i < 50; i++) {
            BenchmarkScenarioFactory.Scenario scenario = BenchmarkScenarioFactory.generateFeasibleScenario(random, i);
            for (StrategyRunContext context : contexts.values()) {
                context.strategy().solve(scenario.constraints(), scenario.priceData(), scenario.co2Data());
            }
        }

        // 2. Measurement Phase
        for (int i = 0; i < scenarios; i++) {
            BenchmarkScenarioFactory.Scenario scenario = BenchmarkScenarioFactory.generateFeasibleScenario(random, i);

            for (StrategyRunContext context : contexts.values()) {
                TimedExecution execution = runTimed(context.strategy(), scenario);
                appendMeasurements(context, execution, scenario);
            }
        }

        AdminBenchmarkResponse.StrategyMetrics optimalMetrics = toResponseMetrics(contexts.get(StrategyId.OPTIMAL));
        AdminBenchmarkResponse.StrategyMetrics greedyMetrics = toResponseMetrics(contexts.get(StrategyId.GREEDY));
        AdminBenchmarkResponse.StrategyMetrics mipMetrics = toResponseMetrics(contexts.get(StrategyId.MIP));
        AdminBenchmarkResponse.StrategyMetrics naiveMetrics = toResponseMetrics(contexts.get(StrategyId.NAIVE));

        return new AdminBenchmarkResponse(scenarios, seed, optimalMetrics, greedyMetrics, mipMetrics, naiveMetrics);
    }

    private ChargingStrategy strategyFor(StrategyId strategyId) {
        return switch (strategyId) {
            case OPTIMAL -> optimalStrategy;
            case GREEDY -> greedyStrategy;
            case MIP -> mipStrategy;
            case NAIVE -> naiveStrategy;
        };
    }

    private static StrategyRunContext newRunContext(ChargingStrategy strategy) {
        return new StrategyRunContext(strategy, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    private static TimedExecution runTimed(ChargingStrategy strategy, BenchmarkScenarioFactory.Scenario scenario) {
        long start = System.nanoTime();
        ScheduleResult result = strategy.solve(scenario.constraints(), scenario.priceData(), scenario.co2Data());
        return new TimedExecution(result, nanosToMs(System.nanoTime() - start));
    }

    private static void appendMeasurements(
            StrategyRunContext context,
            TimedExecution execution,
            BenchmarkScenarioFactory.Scenario scenario
    ) {
        ScheduleResult result = execution.result();
        double objective = objectiveValue(
                result.slots(),
                scenario.priceData(),
                scenario.co2Data(),
                scenario.constraints().weightPrice(),
                scenario.constraints().weightCO2(),
                scenario.constraints().plugInTime(),
                scenario.constraints().departureTime());

        DynamicProgrammingChargingStrategy.RealWorldCostBreakdown realCost =
                DynamicProgrammingChargingStrategy.calculateRealWorldCost(
                        result.slots(),
                        scenario.constraints(),
                        scenario.priceData(),
                        scenario.co2Data());

        context.objectives().add(objective);
        context.costs().add(realCost.totalRealWorldCost());
        context.emissions().add(result.totalPredictedEmissions());
        context.runtimeMs().add(execution.runtimeMs());
    }

    private static AdminBenchmarkResponse.StrategyMetrics toResponseMetrics(StrategyRunContext context) {
        return buildMetrics(context.objectives(), context.costs(), context.emissions(), context.runtimeMs());
    }

    private static AdminBenchmarkResponse.StrategyMetrics buildMetrics(List<Double> obj, List<Double> cost, List<Double> co2, List<Double> rt) {
        MetricSummary o = MetricSummary.of(obj);
        MetricSummary c = MetricSummary.of(cost);
        MetricSummary e = MetricSummary.of(co2);
        MetricSummary r = MetricSummary.of(rt);
        return new AdminBenchmarkResponse.StrategyMetrics(
                new AdminBenchmarkResponse.MetricSummary(o.mean(), o.p50(), o.p95(), o.max()),
                new AdminBenchmarkResponse.MetricSummary(c.mean(), c.p50(), c.p95(), c.max()),
                new AdminBenchmarkResponse.MetricSummary(e.mean(), e.p50(), e.p95(), e.max()),
                new AdminBenchmarkResponse.MetricSummary(r.mean(), r.p50(), r.p95(), r.max())
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

    private static double nanosToMs(long nanos) {
        return nanos / 1_000_000.0;
    }

    private record StrategyRunContext(
            ChargingStrategy strategy,
            List<Double> objectives,
            List<Double> costs,
            List<Double> emissions,
            List<Double> runtimeMs
    ) {
    }

    private record TimedExecution(ScheduleResult result, double runtimeMs) {
    }

    private enum StrategyId {
        OPTIMAL,
        GREEDY,
        MIP,
        NAIVE
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
