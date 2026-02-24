package com.sdu.evcharging.dto.ingest;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EdsApiResponse<T>(
        @JsonProperty("total")
        Integer total,
        
        @JsonProperty("records")
        List<T> records
) {}