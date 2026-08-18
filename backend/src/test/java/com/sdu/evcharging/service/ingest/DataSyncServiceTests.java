package com.sdu.evcharging.service.ingest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sdu.evcharging.domain.CO2Intensity;
import com.sdu.evcharging.domain.EnergyPrice;
import com.sdu.evcharging.dto.ingest.EdsCO2Record;
import com.sdu.evcharging.dto.ingest.EdsSpotPriceRecord;
import com.sdu.evcharging.repository.CO2IntensityRepository;
import com.sdu.evcharging.repository.EnergyPriceRepository;

@ExtendWith(MockitoExtension.class)
class DataSyncServiceTests {

    @Mock
    private EnergyDataIngestService ingestService;

    @Mock
    private EnergyPriceRepository energyPriceRepository;

    @Mock
    private CO2IntensityRepository co2IntensityRepository;

    private DataSyncService dataSyncService;

    @BeforeEach
    void setUp() {
        dataSyncService = new DataSyncService(ingestService, energyPriceRepository, co2IntensityRepository);
    }

    @Test
    void syncSpotPrices_InsertsNewHourlyPrice() {
        LocalDate date = LocalDate.of(2026, 3, 13);
        LocalDateTime hour = LocalDateTime.of(2026, 3, 13, 0, 0);

        doReturn(List.of(new EdsSpotPriceRecord(
                "2026-03-13T00:00:00",
                "2026-03-13T01:00:00",
                "DK1",
                null,
                200.0
        ))).when(ingestService).fetchSpotPrices(date, "DK1");
        doReturn(Optional.empty()).when(energyPriceRepository).findByHourUtcAndPriceArea(hour, "DK1");

        dataSyncService.syncSpotPrices(date, "DK1");

        ArgumentCaptor<EnergyPrice> captor = ArgumentCaptor.forClass(EnergyPrice.class);
        verify(energyPriceRepository).save(captor.capture());

        EnergyPrice saved = captor.getValue();
        assertEquals(hour, saved.getHourUtc());
        assertEquals("DK1", saved.getPriceArea());
        assertEquals(0.2, saved.getPriceDkkPerKwh(), 1e-9);
    }

    @Test
    void syncSpotPrices_UpdatesExistingHourlyPriceWhenValueChanges() {
        LocalDate date = LocalDate.of(2026, 3, 13);
        LocalDateTime hour = LocalDateTime.of(2026, 3, 13, 1, 0);

        EnergyPrice existing = EnergyPrice.builder()
                .id(4L)
                .hourUtc(hour)
                .priceArea("DK1")
                .priceDkkPerKwh(0.1)
                .build();

        doReturn(List.of(new EdsSpotPriceRecord(
                "2026-03-13T01:00:00",
                "2026-03-13T02:00:00",
                "DK1",
                null,
                300.0
        ))).when(ingestService).fetchSpotPrices(date, "DK1");
        doReturn(Optional.of(existing)).when(energyPriceRepository).findByHourUtcAndPriceArea(hour, "DK1");

        dataSyncService.syncSpotPrices(date, "DK1");

        verify(energyPriceRepository).save(existing);
        assertEquals(0.3, existing.getPriceDkkPerKwh(), 1e-9);
    }

    @Test
    void syncCO2Data_SkipsSavingDuplicateRecords() {
        LocalDate date = LocalDate.of(2026, 3, 13);
        LocalDateTime timestamp = LocalDateTime.of(2026, 3, 13, 0, 0);

        doReturn(List.of(new EdsCO2Record(
                "2026-03-13T00:00:00",
                "2026-03-13T01:00:00",
                "DK1",
                155.0
        ))).when(ingestService).fetchCO2Data(date, "DK1");
        doReturn(true).when(co2IntensityRepository).existsByTimestampUtcAndPriceArea(timestamp, "DK1");

        dataSyncService.syncCO2Data(date, "DK1");

        verify(co2IntensityRepository, never()).save(org.mockito.ArgumentMatchers.any(CO2Intensity.class));
    }
}