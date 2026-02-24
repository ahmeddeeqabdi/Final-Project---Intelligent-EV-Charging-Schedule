package com.sdu.evcharging.dto.ingest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EdsSpotPriceRecord(
        @JsonProperty("HourUTC")
        String hourUTC,

        @JsonProperty("HourDK")
        String hourDK,

        @JsonProperty("PriceArea")
        String priceArea,

        @JsonProperty("SpotPriceDKK")
        Double spotPriceDKK
) {}