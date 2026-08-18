package com.sdu.evcharging.dto.auth;

public record LoginRequest(
        String email,
        String password
) {
}
