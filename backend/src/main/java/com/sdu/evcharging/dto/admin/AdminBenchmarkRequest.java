package com.sdu.evcharging.dto.admin;

public record AdminBenchmarkRequest(
        Integer scenarios,
        Long seed
) {
}
