package com.sdu.evcharging.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.sdu.evcharging.dto.ErrorResponse;
import com.sdu.evcharging.service.auth.InvalidCredentialsException;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleIllegalArgumentException_ReturnsBadRequestErrorResponse() {
        var response = handler.handleIllegalArgumentException(new IllegalArgumentException("Zone must be DK1 or DK2"));

        assertEquals(400, response.getStatusCode().value());
        ErrorResponse body = response.getBody();
        assertEquals("Zone must be DK1 or DK2", body.message());
        assertEquals("IllegalArgumentException", body.error());
        assertEquals(400, body.status());
    }

    @Test
    void handleInvalidCredentialsException_ReturnsUnauthorizedErrorResponse() {
        var response = handler.handleInvalidCredentialsException(new InvalidCredentialsException("Invalid email or password"));

        assertEquals(401, response.getStatusCode().value());
        ErrorResponse body = response.getBody();
        assertEquals("Invalid email or password", body.message());
        assertEquals("InvalidCredentialsException", body.error());
        assertEquals(401, body.status());
    }
}