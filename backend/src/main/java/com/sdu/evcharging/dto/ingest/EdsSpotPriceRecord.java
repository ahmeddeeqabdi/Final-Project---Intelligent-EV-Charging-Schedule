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

                @JsonProperty("DayAheadPriceEUR")
                Double dayAheadPriceEUR,

                @JsonProperty("DayAheadPriceDKK")
                Double dayAheadPriceDKK
) {
        private static final double EUR_TO_DKK_FALLBACK_RATE = 7.45;

        public Double effectivePriceDkk() {
                if (dayAheadPriceDKK != null) {
                        return dayAheadPriceDKK;
                }
                if (dayAheadPriceEUR != null) {
                        return dayAheadPriceEUR * EUR_TO_DKK_FALLBACK_RATE;
                }
                return null;
        }
}