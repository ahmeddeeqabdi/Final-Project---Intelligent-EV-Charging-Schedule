package com.sdu.evcharging.dto.ingest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EdsCO2Record(

        // CO2Emis dataset with 5-minute intervals
        @JsonProperty("Minutes5UTC")
        String minutes5UTC,

        @JsonProperty("Minutes5DK")
        String minutes5DK,

        @JsonProperty("PriceArea")
        String priceArea,

        // Unit: gCO2/kWh
        @JsonProperty("CO2Emission")
        Double co2Emission
) {}