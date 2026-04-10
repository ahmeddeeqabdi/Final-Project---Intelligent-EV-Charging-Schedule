package com.sdu.evcharging.service.auth;

import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sdu.evcharging.domain.UserConstraints;
import com.sdu.evcharging.dto.auth.UpdateUserConstraintsRequest;
import com.sdu.evcharging.dto.auth.UserConstraintsResponse;
import com.sdu.evcharging.repository.UserConstraintsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserConstraintsService {

    private static final Set<String> ALLOWED_ZONES = Set.of("DK1", "DK2");

    private final UserConstraintsRepository userConstraintsRepository;

    @Transactional(readOnly = true)
    public UserConstraintsResponse getByUserId(Long userId) {
        UserConstraints constraints = findByUserId(userId);
        return toResponse(constraints);
    }

    @Transactional(readOnly = true)
    public String getPriceAreaByUserId(Long userId) {
        return normalizeZone(findByUserId(userId).getPriceArea());
    }

    @Transactional
    public UserConstraintsResponse updateByUserId(Long userId, UpdateUserConstraintsRequest request) {
        if (request.defaultBatteryCapacity() <= 0 || request.defaultMaxPower() <= 0) {
            throw new IllegalArgumentException("Battery capacity and max power must be positive values");
        }
        if (request.defaultPreferenceWeight() < 0 || request.defaultPreferenceWeight() > 1) {
            throw new IllegalArgumentException("Preference weight must be between 0 and 1");
        }
        String priceArea = normalizeZone(request.priceArea());

        UserConstraints constraints = findByUserId(userId);
        constraints.setDefaultBatteryCapacity(request.defaultBatteryCapacity());
        constraints.setDefaultMaxPower(request.defaultMaxPower());
        constraints.setDefaultPreferenceWeight(request.defaultPreferenceWeight());
        constraints.setPriceArea(priceArea);

        UserConstraints saved = userConstraintsRepository.save(constraints);
        return toResponse(saved);
    }

    private UserConstraints findByUserId(Long userId) {
        return userConstraintsRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Constraints not found for current user"));
    }

    private static UserConstraintsResponse toResponse(UserConstraints constraints) {
        return new UserConstraintsResponse(
                constraints.getDefaultBatteryCapacity(),
                constraints.getDefaultMaxPower(),
                constraints.getDefaultPreferenceWeight(),
                constraints.getPriceArea()
        );
    }

    private static String normalizeZone(String zone) {
        if (zone == null || zone.isBlank()) {
            throw new IllegalArgumentException("Price area must be DK1 or DK2");
        }

        String normalized = zone.trim().toUpperCase();
        if (!ALLOWED_ZONES.contains(normalized)) {
            throw new IllegalArgumentException("Price area must be DK1 or DK2");
        }

        return normalized;
    }
}
