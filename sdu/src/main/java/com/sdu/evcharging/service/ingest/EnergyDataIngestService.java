package com.sdu.evcharging.service.ingest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.sdu.evcharging.dto.ingest.EdsApiResponse;
import com.sdu.evcharging.dto.ingest.EdsCO2Record;
import com.sdu.evcharging.dto.ingest.EdsSpotPriceRecord;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class EnergyDataIngestService {

    private final WebClient webClient;
    private static final DateTimeFormatter EDS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    public List<EdsSpotPriceRecord> fetchSpotPrices(LocalDate date, String zone) {
        String startStr = date.atStartOfDay().format(EDS_DATE_FORMAT);
        String endStr = date.plusDays(1).atStartOfDay().format(EDS_DATE_FORMAT);
        String filterJson = "{\"PriceArea\":[\"" + zone + "\"]}";

        log.info("Requesting DayAheadPrices | Zone: {} | Start: {} | End: {}", zone, startStr, endStr);

        try {
            EdsApiResponse<EdsSpotPriceRecord> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/dataset/DayAheadPrices")
                            .queryParam("start", startStr)
                            .queryParam("end", endStr)
                            .queryParam("filter", "{filter}")
                            .queryParam("sort", "TimeUTC ASC")
                            .build(filterJson))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<EdsApiResponse<EdsSpotPriceRecord>>() {})
                    .block();

            List<EdsSpotPriceRecord> raw = (response != null && response.records() != null)
                    ? response.records() : List.of();

            log.info("Raw 15-min records from DayAheadPrices (zone={}, date={}):", zone, date);
            raw.forEach(r -> log.info("  {} UTC -> {} DKK/MWh", r.timeUTC(), r.dayAheadPriceDKK()));

            // DayAheadPrices is 15-min resolution — aggregate to hourly averages
            List<EdsSpotPriceRecord> hourly = aggregateToHourly(raw);

            log.info("Aggregated to hourly (zone={}, date={}):", zone, date);
            hourly.forEach(r -> log.info("  {} UTC -> {} DKK/MWh (avg of 4 × 15-min)", r.timeUTC(), r.dayAheadPriceDKK()));

            return hourly;

        } catch (Exception e) {
            log.error("EDS API Error: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Collapses 15-minute DayAheadPrices records into hourly slots by
     * grouping on the truncated UTC hour and averaging the price.
     */
    private List<EdsSpotPriceRecord> aggregateToHourly(List<EdsSpotPriceRecord> raw) {
        // Key = "YYYY-MM-DDTHH" (first 13 chars of TimeUTC)
        Map<String, List<EdsSpotPriceRecord>> byHour = raw.stream()
                .filter(r -> r.timeUTC() != null)
                .collect(Collectors.groupingBy(r -> r.timeUTC().substring(0, 13)));

        return byHour.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<EdsSpotPriceRecord> slots = entry.getValue();
                    double avgPriceDKK = slots.stream()
                            .mapToDouble(r -> r.dayAheadPriceDKK() != null ? r.dayAheadPriceDKK() : 0.0)
                            .average()
                            .orElse(0.0);
                    // Use the :00:00 version as the canonical hour timestamp
                    String hourUtc = entry.getKey() + ":00:00";
                    String hourDk  = slots.get(0).timeDK() != null
                            ? slots.get(0).timeDK().substring(0, 13) + ":00:00"
                            : hourUtc;
                    return new EdsSpotPriceRecord(hourUtc, hourDk, slots.get(0).priceArea(), avgPriceDKK);
                })
                .collect(Collectors.toList());
    }

    public List<EdsCO2Record> fetchCO2Data(LocalDate date, String zone) {
        String startStr = date.atStartOfDay().format(EDS_DATE_FORMAT);
        String endStr = date.plusDays(1).atStartOfDay().format(EDS_DATE_FORMAT);
        String filterJson = "{\"PriceArea\":[\"" + zone + "\"]}";

        try {
            EdsApiResponse<EdsCO2Record> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/dataset/CO2Emis")
                            .queryParam("start", startStr)
                            .queryParam("end", endStr)
                            .queryParam("filter", "{filter}")
                            .queryParam("sort", "Minutes5UTC ASC")
                            .build(filterJson))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<EdsApiResponse<EdsCO2Record>>() {})
                    .block();

            return (response != null && response.records() != null) ? response.records() : List.of();
        } catch (Exception e) {
            log.error("CO2 Data Fetch Failed: {}", e.getMessage());
            return List.of();
        }
    }
}