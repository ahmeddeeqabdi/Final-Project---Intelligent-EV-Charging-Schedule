package com.sdu.evcharging.service.ingest;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.sdu.evcharging.domain.CO2Intensity;
import com.sdu.evcharging.domain.EnergyPrice;
import com.sdu.evcharging.dto.ingest.EdsApiResponse;
import com.sdu.evcharging.dto.ingest.EdsCO2Record;
import com.sdu.evcharging.dto.ingest.EdsSpotPriceRecord;
import com.sdu.evcharging.repository.CO2IntensityRepository;
import com.sdu.evcharging.repository.EnergyPriceRepository;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EnergyDataIngestService {

    private final WebClient webClient;
    private final EnergyPriceRepository energyPriceRepository;
    private final CO2IntensityRepository co2IntensityRepository;
    private final CircuitBreaker spotPriceBreaker;
    private final CircuitBreaker co2Breaker;
    private final Duration requestTimeout;
    private final long fallbackMaxAgeHours;

    private static final DateTimeFormatter EDS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public EnergyDataIngestService(
            WebClient webClient,
            EnergyPriceRepository energyPriceRepository,
            CO2IntensityRepository co2IntensityRepository,
            @Value("${resilience.eds.failure-rate-threshold:50}") float failureRateThreshold,
            @Value("${resilience.eds.sliding-window-size:10}") int slidingWindowSize,
            @Value("${resilience.eds.wait-duration-open-seconds:30}") long waitDurationOpenSeconds,
            @Value("${resilience.eds.permitted-half-open-calls:3}") int permittedHalfOpenCalls,
            @Value("${resilience.eds.request-timeout-seconds:8}") long requestTimeoutSeconds,
            @Value("${resilience.eds.fallback-max-age-hours:48}") long fallbackMaxAgeHours
    ) {
        this.webClient = webClient;
        this.energyPriceRepository = energyPriceRepository;
        this.co2IntensityRepository = co2IntensityRepository;
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
        this.fallbackMaxAgeHours = fallbackMaxAgeHours;

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .slidingWindowSize(slidingWindowSize)
                .permittedNumberOfCallsInHalfOpenState(permittedHalfOpenCalls)
                .waitDurationInOpenState(Duration.ofSeconds(waitDurationOpenSeconds))
                .recordException(throwable -> true)
                .build();

        this.spotPriceBreaker = CircuitBreaker.of("edsSpotPrices", config);
        this.co2Breaker = CircuitBreaker.of("edsCo2", config);
        this.spotPriceBreaker.getEventPublisher().onStateTransition(event ->
                log.warn("Circuit breaker transition [name={}] {}", event.getCircuitBreakerName(), event.getStateTransition()));
        this.co2Breaker.getEventPublisher().onStateTransition(event ->
                log.warn("Circuit breaker transition [name={}] {}", event.getCircuitBreakerName(), event.getStateTransition()));
    }

    public List<EdsSpotPriceRecord> fetchSpotPrices(LocalDate date, String zone) {
        try {
            return CircuitBreaker.decorateSupplier(spotPriceBreaker, () -> fetchSpotPricesFromApi(date, zone)).get();
        } catch (Exception ex) {
            return fallbackSpotPrices(date, zone, ex);
        }
    }

    public List<EdsCO2Record> fetchCO2Data(LocalDate date, String zone) {
        try {
            return CircuitBreaker.decorateSupplier(co2Breaker, () -> fetchCo2FromApi(date, zone)).get();
        } catch (Exception ex) {
            return fallbackCo2(date, zone, ex);
        }
    }

    private List<EdsSpotPriceRecord> fetchSpotPricesFromApi(LocalDate date, String zone) {
        String startStr = date.atStartOfDay().format(EDS_DATE_FORMAT);
        String endStr = date.plusDays(1).atStartOfDay().format(EDS_DATE_FORMAT);
        String filterJson = "{\"PriceArea\":[\"" + zone + "\"]}";

        log.info("Requesting DayAheadPrices | Zone: {} | Start: {} | End: {}", zone, startStr, endStr);

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
                .block(requestTimeout);

        List<EdsSpotPriceRecord> raw = (response != null && response.records() != null)
                ? response.records() : List.of();

        log.info("Raw 15-min records from DayAheadPrices (zone={}, date={}):", zone, date);
        raw.forEach(r -> log.info("  {} UTC -> {} DKK/MWh", r.timeUTC(), r.dayAheadPriceDKK()));

        
        List<EdsSpotPriceRecord> hourly = aggregateToHourly(raw);

        log.info("Aggregated to hourly (zone={}, date={}):", zone, date);
        hourly.forEach(r -> log.info("  {} UTC -> {} DKK/MWh (avg of 4 x 15-min)", r.timeUTC(), r.dayAheadPriceDKK()));

        return hourly;
    }

    



    private List<EdsSpotPriceRecord> aggregateToHourly(List<EdsSpotPriceRecord> raw) {
        
        Map<String, List<EdsSpotPriceRecord>> byHour = raw.stream()
                .filter(r -> r.timeUTC() != null)
                .collect(Collectors.groupingBy(r -> r.timeUTC().substring(0, 13)));

        return byHour.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<EdsSpotPriceRecord> slots = entry.getValue();
                    double avgPriceDKK = slots.stream()
                                                        .mapToDouble(r -> nullableDouble(r.dayAheadPriceDKK()))
                            .average()
                            .orElse(0.0);
                    
                    String hourUtc = entry.getKey() + ":00:00";
                    String hourDk  = slots.get(0).timeDK() != null
                            ? slots.get(0).timeDK().substring(0, 13) + ":00:00"
                            : hourUtc;
                    return new EdsSpotPriceRecord(hourUtc, hourDk, slots.get(0).priceArea(), avgPriceDKK);
                })
                .collect(Collectors.toList());
    }

    private List<EdsCO2Record> fetchCo2FromApi(LocalDate date, String zone) {
        String startStr = date.atStartOfDay().format(EDS_DATE_FORMAT);
        String endStr = date.plusDays(1).atStartOfDay().format(EDS_DATE_FORMAT);
        String filterJson = "{\"PriceArea\":[\"" + zone + "\"]}";

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
                .block(requestTimeout);

        return (response != null && response.records() != null) ? response.records() : List.of();
    }

    private List<EdsSpotPriceRecord> fallbackSpotPrices(LocalDate requestedDate, String zone, Exception ex) {
        log.warn("DayAheadPrices fallback activated [zone={}, date={}] reason={}", zone, requestedDate, ex.getMessage());

        LocalDateTime requestedStart = requestedDate.atStartOfDay();
        LocalDateTime requestedEnd = requestedDate.plusDays(1).atStartOfDay();
        List<EnergyPrice> exact = energyPriceRepository.findByPriceAreaAndHourUtcBetweenOrderByHourUtcAsc(
                zone, requestedStart, requestedEnd);
        if (!exact.isEmpty()) {
            return toEdsPriceRecords(exact);
        }

        return energyPriceRepository.findTopByPriceAreaOrderByHourUtcDesc(zone)
                .map(latest -> {
                    long dataAgeHours = Duration.between(latest.getHourUtc(), requestedStart).toHours();
                    if (dataAgeHours > fallbackMaxAgeHours) {
                        log.warn("Skipping stale spot fallback [zone={}] dataAgeHours={} limit={}",
                                zone, dataAgeHours, fallbackMaxAgeHours);
                        return List.<EdsSpotPriceRecord>of();
                    }

                    LocalDate fallbackDate = latest.getHourUtc().toLocalDate();
                    List<EnergyPrice> latestDay = energyPriceRepository.findByPriceAreaAndHourUtcBetweenOrderByHourUtcAsc(
                            zone,
                            fallbackDate.atStartOfDay(),
                            fallbackDate.plusDays(1).atStartOfDay());

                    if (!latestDay.isEmpty()) {
                        log.warn("Using cached spot fallback [zone={}] sourceDate={} ageHours={}",
                                zone, fallbackDate, Math.max(0, dataAgeHours));
                    }
                    return toEdsPriceRecords(latestDay);
                })
                .orElseGet(List::of);
    }

    private List<EdsCO2Record> fallbackCo2(LocalDate requestedDate, String zone, Exception ex) {
        log.warn("CO2 fallback activated [zone={}, date={}] reason={}", zone, requestedDate, ex.getMessage());

        LocalDateTime requestedStart = requestedDate.atStartOfDay();
        LocalDateTime requestedEnd = requestedDate.plusDays(1).atStartOfDay();
        List<CO2Intensity> exact = co2IntensityRepository.findByPriceAreaAndTimestampUtcBetweenOrderByTimestampUtcAsc(
                zone, requestedStart, requestedEnd);
        if (!exact.isEmpty()) {
            return toEdsCo2Records(exact);
        }

        return co2IntensityRepository.findTopByPriceAreaOrderByTimestampUtcDesc(zone)
                .map(latest -> {
                    long dataAgeHours = Duration.between(latest.getTimestampUtc(), requestedStart).toHours();
                    if (dataAgeHours > fallbackMaxAgeHours) {
                        log.warn("Skipping stale CO2 fallback [zone={}] dataAgeHours={} limit={}",
                                zone, dataAgeHours, fallbackMaxAgeHours);
                        return List.<EdsCO2Record>of();
                    }

                    LocalDate fallbackDate = latest.getTimestampUtc().toLocalDate();
                    List<CO2Intensity> latestDay = co2IntensityRepository.findByPriceAreaAndTimestampUtcBetweenOrderByTimestampUtcAsc(
                            zone,
                            fallbackDate.atStartOfDay(),
                            fallbackDate.plusDays(1).atStartOfDay());

                    if (!latestDay.isEmpty()) {
                        log.warn("Using cached CO2 fallback [zone={}] sourceDate={} ageHours={}",
                                zone, fallbackDate, Math.max(0, dataAgeHours));
                    }
                    return toEdsCo2Records(latestDay);
                })
                .orElseGet(List::of);
    }

    private static List<EdsSpotPriceRecord> toEdsPriceRecords(List<EnergyPrice> prices) {
        return prices.stream()
                .map(price -> {
                    String iso = price.getHourUtc().format(ISO_FORMAT);
                    return new EdsSpotPriceRecord(
                            iso,
                            iso,
                            price.getPriceArea(),
                            price.getPriceDkkPerKwh() * 1000.0
                    );
                })
                .toList();
    }

    private static List<EdsCO2Record> toEdsCo2Records(List<CO2Intensity> values) {
        return values.stream()
                .map(co2 -> {
                    String iso = co2.getTimestampUtc().format(ISO_FORMAT);
                    return new EdsCO2Record(
                            iso,
                            iso,
                            co2.getPriceArea(),
                            co2.getGPerKwh()
                    );
                })
                .toList();
    }

        private static double nullableDouble(Double value) {
                return value == null ? 0.0 : value;
        }
}