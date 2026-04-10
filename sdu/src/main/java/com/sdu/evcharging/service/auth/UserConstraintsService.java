package com.sdu.evcharging.service.auth;

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

    private final UserConstraintsRepository userConstraintsRepository;

    @Transactional(readOnly = true)
    public UserConstraintsResponse getByUserId(Long userId) {
        UserConstraints constraints = findByUserId(userId);
        return toResponse(constraints);
    }

    @Transactional
    public UserConstraintsResponse updateByUserId(Long userId, UpdateUserConstraintsRequest request) {
        if (request.defaultBatteryCapacity() <= 0 || request.defaultMaxPower() <= 0) {
            throw new IllegalArgumentException("Battery capacity and max power must be positive values");
        }
        if (request.defaultPreferenceWeight() < 0 || request.defaultPreferenceWeight() > 1) {
            throw new IllegalArgumentException("Preference weight must be between 0 and 1");
        }

        UserConstraints constraints = findByUserId(userId);
        constraints.setDefaultBatteryCapacity(request.defaultBatteryCapacity());
        constraints.setDefaultMaxPower(request.defaultMaxPower());
        constraints.setDefaultPreferenceWeight(request.defaultPreferenceWeight());

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
                constraints.getDefaultPreferenceWeight()
        );
    }
}
