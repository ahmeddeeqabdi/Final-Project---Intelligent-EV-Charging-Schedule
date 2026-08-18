package com.sdu.evcharging.dto.admin;

public record AdminBenchmarkResponse(
        int scenarios,
        long seed,
        StrategyMetrics optimal,
        StrategyMetrics greedy,
        StrategyMetrics mip,
        StrategyMetrics naive
) {
    public record StrategyMetrics(
            MetricSummary objective,
            MetricSummary cost,
            MetricSummary emissions,
            MetricSummary runtimeMs
    ) {}

    public record MetricSummary(double mean, double p50, double p95, double max) {}
}
