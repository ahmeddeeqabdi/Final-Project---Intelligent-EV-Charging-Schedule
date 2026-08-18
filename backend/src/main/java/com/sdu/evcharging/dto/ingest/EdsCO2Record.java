package com.sdu.evcharging.dto.ingest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EdsCO2Record(

        
        @JsonProperty("Minutes5UTC")
        String minutes5UTC,

        @JsonProperty("Minutes5DK")
        String minutes5DK,

        @JsonProperty("PriceArea")
        String priceArea,

        
        @JsonProperty("CO2Emission")
        Double co2Emission
) {}