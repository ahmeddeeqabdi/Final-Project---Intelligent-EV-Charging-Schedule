package com.sdu.evcharging.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ErrorResponse(
        @JsonProperty("message")
        String message,
        
        @JsonProperty("error")
        String error,
        
        @JsonProperty("status")
        int status
) {}
