package com.sdu.evcharging.controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sdu.evcharging.dto.schedule.ScheduleRequest;
import com.sdu.evcharging.dto.schedule.ScheduleResult;
import com.sdu.evcharging.service.ingest.DataSyncService;
import com.sdu.evcharging.service.optimize.SchedulingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/schedule")
@RequiredArgsConstructor
@Slf4j
public class ScheduleController {

    private static final Set<String> ALLOWED_ZONES = Set.of("DK1", "DK2");

    private final SchedulingService schedulingService;
    private final DataSyncService dataSyncService;


    @PostMapping
    public ResponseEntity<ScheduleResult> createSchedule(
            @RequestBody ScheduleRequest request,
            @RequestParam(defaultValue = "naive") String algorithm
    ) {
        validateZone(request.priceZone());

        log.info("POST /api/v1/schedule [algorithm={}] zone={} departure={}",
                algorithm, request.priceZone(), request.departureTime());

        ScheduleResult schedule = schedulingService.createSchedule(request, algorithm);
        return ResponseEntity.ok(schedule);
    }

    @PostMapping("/sync")
    public ResponseEntity<String> syncData(
            @RequestParam(defaultValue = "today") String date,
            @RequestParam(required = false) String zone
    ) {
        log.info("POST /api/v1/schedule/sync [date={}] [zone={}]", date, zone);

        LocalDate targetDate = parseDateParam(date);
        List<String> zones = zone != null ? List.of(normalizeAndValidateZone(zone)) : List.of("DK1", "DK2");
        
        for (String z : zones) {
            dataSyncService.syncSpotPrices(targetDate, z);
            dataSyncService.syncCO2Data(targetDate, z);
        }
        
        return ResponseEntity.ok("Data synced for " + targetDate + " in zones: " + zones);
    }

    private static void validateZone(String zone) {
        normalizeAndValidateZone(zone);
    }

    private static String normalizeAndValidateZone(String zone) {
        if (zone == null || zone.isBlank()) {
            throw new IllegalArgumentException("Zone must be DK1 or DK2");
        }

        String normalized = zone.trim().toUpperCase();
        if (!ALLOWED_ZONES.contains(normalized)) {
            throw new IllegalArgumentException("Zone must be DK1 or DK2");
        }

        return normalized;
    }

    private static LocalDate parseDateParam(String date) {
        if ("today".equalsIgnoreCase(date)) {
            return LocalDate.now();
        }
        if ("tomorrow".equalsIgnoreCase(date)) {
            return LocalDate.now().plusDays(1);
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Date must be 'today', 'tomorrow', or ISO format yyyy-MM-dd");
        }
    }
}