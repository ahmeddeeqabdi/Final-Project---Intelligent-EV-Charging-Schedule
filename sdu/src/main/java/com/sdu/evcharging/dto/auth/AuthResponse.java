package com.sdu.evcharging.dto.auth;

public record AuthResponse(
        String token,
        UserSummaryResponse user
) {
}
