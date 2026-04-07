package com.sdu.evcharging.dto.ingest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;





@JsonIgnoreProperties(ignoreUnknown = true)
public record EdsSpotPriceRecord(
        @JsonProperty("TimeUTC")
        String timeUTC,

        @JsonProperty("TimeDK")
        String timeDK,

        @JsonProperty("PriceArea")
        String priceArea,

        
        @JsonProperty("DayAheadPriceDKK")
        Double dayAheadPriceDKK
) {}