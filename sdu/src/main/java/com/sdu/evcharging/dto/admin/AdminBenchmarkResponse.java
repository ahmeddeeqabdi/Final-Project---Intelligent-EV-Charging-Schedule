package com.sdu.evcharging.dto.admin;

public record AdminBenchmarkResponse(
        int scenarios,
        long seed,
        MetricSummary objectiveGapPercent,
        MetricSummary costGapPercent,
        MetricSummary emissionsGapPercent,
        RuntimeSummary runtimeMs
) {
    public record MetricSummary(double mean, double p50, double p95, double max) {
    }

    public record RuntimeSummary(double optimalMeanMs, double greedyMeanMs, double overheadDeltaMs) {
    }
}
