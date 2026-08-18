package com.sdu.evcharging.service.ingest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GridDataSyncServiceTests {

    @Mock
    private DataSyncService dataSyncService;

    @InjectMocks
    private GridDataSyncService gridDataSyncService;

    @Test
    void syncForDate_RunsBothSyncsForSingleZone() {
        LocalDate date = LocalDate.of(2026, 3, 13);

        String summary = gridDataSyncService.syncForDate(date, "DK1", "manual");

        verify(dataSyncService).syncSpotPrices(date, "DK1");
        verify(dataSyncService).syncCO2Data(date, "DK1");
        assertTrue(summary.contains("success=1 failures=0"));
    }

    @Test
    void syncForDate_ReportsFailuresWithoutStoppingOtherZones() {
        LocalDate date = LocalDate.of(2026, 3, 13);
        doThrow(new RuntimeException("CO2 API unavailable")).when(dataSyncService).syncCO2Data(date, "DK2");

        String summary = gridDataSyncService.syncForDate(date, null, "manual");

        verify(dataSyncService).syncSpotPrices(date, "DK1");
        verify(dataSyncService).syncCO2Data(date, "DK1");
        verify(dataSyncService).syncSpotPrices(date, "DK2");
        verify(dataSyncService).syncCO2Data(date, "DK2");
        assertTrue(summary.startsWith("Grid sync complete [source=manual]"));
    }
}