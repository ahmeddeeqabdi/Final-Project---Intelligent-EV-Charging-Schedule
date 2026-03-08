package com.sdu.evcharging.service.ingest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
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

    /**
     * Runs daily at 13:15 CET — Energinet publishes day-ahead prices
     * between 12:00 and 13:00, so 13:15 is a safe trigger window.
     */
    @Scheduled(cron = "0 15 13 * * *")
    public void syncDayAheadData() {
        log.info("=== Daily day-ahead sync triggered ===");
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        for (String zone : ZONES) {
            syncSpotPrices(tomorrow, zone);
            syncCO2Data(tomorrow, zone);
        }
        log.info("=== Daily sync complete ===");
    }

    public void syncSpotPrices(LocalDate date, String zone) {
        ingestService.fetchSpotPrices(date, zone).forEach(record -> {
            LocalDateTime hour = LocalDateTime.parse(record.timeUTC(), ISO_FORMAT);
            if (!energyPriceRepository.existsByHourUtcAndPriceArea(hour, zone)) {
                energyPriceRepository.save(EnergyPrice.builder()
                        .hourUtc(hour)
                        .priceArea(zone)
                        // Convert DKK/MWh → DKK/kWh
                        .priceDkkPerKwh(record.dayAheadPriceDKK() / 1000.0)
                        .build());
            }
        });
        log.info("Spot price sync done: zone={} date={}", zone, date);
    }

    public void syncCO2Data(LocalDate date, String zone) {
        ingestService.fetchCO2Data(date, zone).forEach(record -> {
            LocalDateTime ts = LocalDateTime.parse(record.minutes5UTC(), ISO_FORMAT);
            if (!co2IntensityRepository.existsByTimestampUtcAndPriceArea(ts, zone)) {
                co2IntensityRepository.save(CO2Intensity.builder()
                        .timestampUtc(ts)
                        .priceArea(zone)
                        .gPerKwh(record.co2Emission())
                        .build());
            }
        });
        log.info("CO2 sync done: zone={} date={}", zone, date);
    }
}