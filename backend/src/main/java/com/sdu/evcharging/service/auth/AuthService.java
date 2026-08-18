package com.sdu.evcharging.service.auth;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sdu.evcharging.domain.User;
import com.sdu.evcharging.domain.UserConstraints;
import com.sdu.evcharging.domain.UserRole;
import com.sdu.evcharging.dto.auth.AuthResponse;
import com.sdu.evcharging.dto.auth.LoginRequest;
import com.sdu.evcharging.dto.auth.SignupRequest;
import com.sdu.evcharging.dto.auth.UserSummaryResponse;
import com.sdu.evcharging.repository.UserRepository;
import com.sdu.evcharging.security.JwtProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final double DEFAULT_BATTERY_CAPACITY = 77.0;
    private static final double DEFAULT_MAX_POWER = 11.0;
    private static final double DEFAULT_PREFERENCE_WEIGHT = 0.5;
    private static final String DEFAULT_PRICE_AREA = "DK2";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        String password = normalizePassword(request.password());

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(UserRole.USER)
                .build();

        user.setConstraints(UserConstraints.builder()
                .defaultBatteryCapacity(DEFAULT_BATTERY_CAPACITY)
                .defaultMaxPower(DEFAULT_MAX_POWER)
                .defaultPreferenceWeight(DEFAULT_PREFERENCE_WEIGHT)
                .priceArea(DEFAULT_PRICE_AREA)
                .build());

        User savedUser = userRepository.save(user);
        String token = jwtProvider.generateToken(savedUser);

        return new AuthResponse(token, toUserSummary(savedUser));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        String password = normalizePassword(request.password());

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String token = jwtProvider.generateToken(user);
        return new AuthResponse(token, toUserSummary(user));
    }

    private static UserSummaryResponse toUserSummary(User user) {
        return new UserSummaryResponse(user.getId(), user.getEmail(), user.getRole().name());
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }
        return password;
    }
}
