package com.sdu.evcharging.dto.auth;

public record SignupRequest(
        String email,
        String password
) {
}
