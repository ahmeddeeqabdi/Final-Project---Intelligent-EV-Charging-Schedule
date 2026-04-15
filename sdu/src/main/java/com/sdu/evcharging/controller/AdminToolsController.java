package com.sdu.evcharging.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sdu.evcharging.dto.admin.AdminBenchmarkRequest;
import com.sdu.evcharging.dto.admin.AdminBenchmarkResponse;
import com.sdu.evcharging.service.optimize.AdminBenchmarkService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminToolsController {

    private final AdminBenchmarkService adminBenchmarkService;

    @PostMapping("/benchmarks/run")
    public ResponseEntity<AdminBenchmarkResponse> runBenchmark(@RequestBody(required = false) AdminBenchmarkRequest request) {
        Integer scenarios = request != null ? request.scenarios() : null;
        Long seed = request != null ? request.seed() : null;
        AdminBenchmarkResponse response = adminBenchmarkService.run(scenarios, seed);
        return ResponseEntity.ok(response);
    }
}
