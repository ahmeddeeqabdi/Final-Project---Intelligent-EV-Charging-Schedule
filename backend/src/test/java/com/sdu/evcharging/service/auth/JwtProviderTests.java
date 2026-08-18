package com.sdu.evcharging.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sdu.evcharging.domain.User;
import com.sdu.evcharging.domain.UserRole;
import com.sdu.evcharging.security.JwtProvider;

class JwtProviderTests {

    @Test
    void generateToken_IsValidAndEncodesEmail() {
        JwtProvider jwtProvider = new JwtProvider(
                "test-only-secret-key-with-at-least-thirty-two-characters",
                3_600_000L
        );
        User user = User.builder()
                .id(7L)
                .email("driver@example.com")
                .passwordHash("hash")
                .role(UserRole.USER)
                .build();

        String token = jwtProvider.generateToken(user);

        assertTrue(jwtProvider.isValid(token));
        assertEquals("driver@example.com", jwtProvider.extractEmail(token));
    }
}