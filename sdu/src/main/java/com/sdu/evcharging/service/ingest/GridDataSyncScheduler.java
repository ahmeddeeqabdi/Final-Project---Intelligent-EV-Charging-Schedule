package com.sdu.evcharging.service.ingest;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class GridDataSyncScheduler {

    private final GridDataSyncService gridDataSyncService;

    @Scheduled(cron = "0 5 13 * * *", zone = "Europe/Copenhagen")
    public void syncGridDataDaily() {
        Thread.startVirtualThread(() -> {
            try {
                String summary = gridDataSyncService.syncTodayAndTomorrowAllZones("scheduled");
                log.info("Daily grid sync finished: {}", summary);
            } catch (Exception ex) {
                log.error("Daily grid sync failed unexpectedly", ex);
            }
        });
    }
}