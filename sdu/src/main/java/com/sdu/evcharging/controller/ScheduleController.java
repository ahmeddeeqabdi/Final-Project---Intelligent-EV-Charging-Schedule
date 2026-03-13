package com.sdu.evcharging.controller;

import java.time.LocalDate;
import java.util.List;

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

    private final SchedulingService schedulingService;
    private final DataSyncService dataSyncService;


    @PostMapping
    public ResponseEntity<ScheduleResult> createSchedule(
            @RequestBody ScheduleRequest request,
            @RequestParam(defaultValue = "naive") String algorithm
    ) {
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
        
        LocalDate targetDate = "today".equalsIgnoreCase(date) ? LocalDate.now() : 
                               "tomorrow".equalsIgnoreCase(date) ? LocalDate.now().plusDays(1) :
                               LocalDate.parse(date);
        
        List<String> zones = zone != null ? List.of(zone) : List.of("DK1", "DK2");
        
        for (String z : zones) {
            dataSyncService.syncSpotPrices(targetDate, z);
            dataSyncService.syncCO2Data(targetDate, z);
        }
        
        return ResponseEntity.ok("Data synced for " + targetDate + " in zones: " + zones);
    }
}