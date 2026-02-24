package com.sdu.evcharging.service.ingest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

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

        log.info("Requesting EDS Spot Prices | Zone: {} | Start: {} | End: {}", zone, startStr, endStr);

        try {
            EdsApiResponse<EdsSpotPriceRecord> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/dataset/Elspotprices")
                            .queryParam("start", startStr)
                            .queryParam("end", endStr)
                            // this is a variable placeholder
                            .queryParam("filter", "{filter}") 
                            .queryParam("sort", "HourUTC ASC")
                            // URL-encodes the JSON and inserts it here
                            .build(filterJson)) 
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<EdsApiResponse<EdsSpotPriceRecord>>() {})
                    .block();

            List<EdsSpotPriceRecord> records = (response != null && response.records() != null)
                    ? response.records() : List.of();

            System.out.println("---------------------------------------------------------");
            System.out.println("SUCCESS! DATA RECEIVED FROM ENERGINET");
            records.forEach(r -> System.out.println("   " + r.hourDK() + " -> " + r.spotPriceDKK() + " DKK/MWh"));
            System.out.println("---------------------------------------------------------");
            
            return records;

        } catch (Exception e) {
            log.error("EDS API Error: {}", e.getMessage());
            throw e;
        }
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