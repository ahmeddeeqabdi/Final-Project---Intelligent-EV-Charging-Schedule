package com.sdu.evcharging.dto.ingest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Maps to the DayAheadPrices dataset (replaced Elspotprices after 2025-09-30).
 * Resolution: 15 minutes. Records are aggregated to hourly in the ingest service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EdsSpotPriceRecord(
        @JsonProperty("TimeUTC")
        String timeUTC,

        @JsonProperty("TimeDK")
        String timeDK,

        @JsonProperty("PriceArea")
        String priceArea,

        // DKK per MWh — converted to DKK/kWh on ingest
        @JsonProperty("DayAheadPriceDKK")
        Double dayAheadPriceDKK
) {}