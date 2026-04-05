package com.sdu.evcharging.service.ingest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class GridDataSyncService {

    public static final List<String> SUPPORTED_ZONES = List.of("DK1", "DK2");

    private final DataSyncService dataSyncService;

    public String syncForDate(LocalDate date, String zoneOrNull, String source) {
        List<String> zones = zoneOrNull != null ? List.of(zoneOrNull) : SUPPORTED_ZONES;
        return syncForDates(List.of(date), zones, source);
    }

    public String syncTodayAndTomorrowAllZones(String source) {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        return syncForDates(List.of(today, tomorrow), SUPPORTED_ZONES, source);
    }

    private String syncForDates(List<LocalDate> dates, List<String> zones, String source) {
        log.info("Grid sync started [source={}] dates={} zones={}", source, dates, zones);

        int successCount = 0;
        List<String> failures = new ArrayList<>();

        for (LocalDate date : dates) {
            for (String zone : zones) {
                try {
                    dataSyncService.syncSpotPrices(date, zone);
                    dataSyncService.syncCO2Data(date, zone);
                    successCount++;
                } catch (Exception ex) {
                    failures.add("date=" + date + ", zone=" + zone + ", error=" + ex.getMessage());
                    log.error("Grid sync failed [source={}] date={} zone={}", source, date, zone, ex);
                }
            }
        }

        String summary = String.format(
                "Grid sync complete [source=%s] success=%d failures=%d",
                source,
                successCount,
                failures.size()
        );

        if (!failures.isEmpty()) {
            log.warn("{} | details={}", summary, failures);
            return summary + " | details=" + failures;
        }

        log.info(summary);
        return summary;
    }
}