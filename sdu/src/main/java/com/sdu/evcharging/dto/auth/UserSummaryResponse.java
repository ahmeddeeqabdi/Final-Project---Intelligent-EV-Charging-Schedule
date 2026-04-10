package com.sdu.evcharging.dto.auth;

public record UserSummaryResponse(
        Long id,
        String email,
        String role
) {
}
