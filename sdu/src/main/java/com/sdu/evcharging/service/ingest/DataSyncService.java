package com.sdu.evcharging.service.ingest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.sdu.evcharging.domain.CO2Intensity;
import com.sdu.evcharging.domain.EnergyPrice;
import com.sdu.evcharging.repository.CO2IntensityRepository;
import com.sdu.evcharging.repository.EnergyPriceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class DataSyncService {

    private static final List<String> ZONES = List.of("DK1", "DK2");
    private static final DateTimeFormatter ISO_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final EnergyDataIngestService ingestService;
    private final EnergyPriceRepository energyPriceRepository;
    private final CO2IntensityRepository co2IntensityRepository;

    public void syncSpotPrices(LocalDate date, String zone) {
        List<com.sdu.evcharging.dto.ingest.EdsSpotPriceRecord> records = ingestService.fetchSpotPrices(date, zone);
        if (records.isEmpty()) {
            log.warn("Spot price sync degraded: no records available [zone={}] [date={}]", zone, date);
            return;
        }

        int inserted = 0;
        for (com.sdu.evcharging.dto.ingest.EdsSpotPriceRecord record : records) {
            LocalDateTime hour = LocalDateTime.parse(record.timeUTC(), ISO_FORMAT);
            if (!energyPriceRepository.existsByHourUtcAndPriceArea(hour, zone)) {
                energyPriceRepository.save(EnergyPrice.builder()
                        .hourUtc(hour)
                        .priceArea(zone)
                        
                        .priceDkkPerKwh(record.dayAheadPriceDKK() / 1000.0)
                        .build());
                inserted++;
            }
        }

        log.info("Spot price sync done: zone={} date={} fetched={} inserted={}", zone, date, records.size(), inserted);
    }

    public void syncCO2Data(LocalDate date, String zone) {
        List<com.sdu.evcharging.dto.ingest.EdsCO2Record> records = ingestService.fetchCO2Data(date, zone);
        if (records.isEmpty()) {
            log.warn("CO2 sync degraded: no records available [zone={}] [date={}]", zone, date);
            return;
        }

        int inserted = 0;
        for (com.sdu.evcharging.dto.ingest.EdsCO2Record record : records) {
            LocalDateTime ts = LocalDateTime.parse(record.minutes5UTC(), ISO_FORMAT);
            if (!co2IntensityRepository.existsByTimestampUtcAndPriceArea(ts, zone)) {
                co2IntensityRepository.save(CO2Intensity.builder()
                        .timestampUtc(ts)
                        .priceArea(zone)
                        .gPerKwh(record.co2Emission())
                        .build());
                inserted++;
            }
        }

        log.info("CO2 sync done: zone={} date={} fetched={} inserted={}", zone, date, records.size(), inserted);
    }

    



    @EventListener(ApplicationReadyEvent.class)
    public void syncHistoricalData() {
        
        
        if (energyPriceRepository.count() < 4000) {
            log.info("=== Database has less than 3 months of data. Starting/Resuming historical data sync ===");
            LocalDate endDate = LocalDate.now().plusDays(1);
            LocalDate startDate = endDate.minusMonths(3);

            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                for (String zone : ZONES) {
                    try {
                        syncSpotPrices(date, zone);
                        syncCO2Data(date, zone);
                        
                        Thread.sleep(100);
                    } catch (Exception e) {
                        log.error("Failed to sync historical data for zone={} date={}", zone, date, e);
                    }
                }
            }
            log.info("=== 3-month historical data sync complete ===");
        } else {
            log.info("=== Database already contains data. Skipping historical 3-month sync. ===");
        }
    }
}