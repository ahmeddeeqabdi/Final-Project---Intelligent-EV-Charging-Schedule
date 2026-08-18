package com.sdu.evcharging.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.sdu.evcharging.domain.User;
import com.sdu.evcharging.domain.UserConstraints;
import com.sdu.evcharging.domain.UserRole;

@SpringBootTest
class UserConstraintsRepositoryTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserConstraintsRepository userConstraintsRepository;

    @Test
    void findByUserId_ReturnsPersistedConstraints() {
        User user = User.builder()
                .email("driver@example.com")
                .passwordHash("hash")
                .role(UserRole.USER)
                .build();
        user.setConstraints(UserConstraints.builder()
                .defaultBatteryCapacity(77.0)
                .defaultMaxPower(11.0)
                .defaultPreferenceWeight(0.55)
                .priceArea("DK1")
                .build());

        User savedUser = userRepository.saveAndFlush(user);

        UserConstraints stored = userConstraintsRepository.findByUserId(savedUser.getId()).orElseThrow();

        assertEquals(77.0, stored.getDefaultBatteryCapacity(), 1e-9);
        assertEquals(11.0, stored.getDefaultMaxPower(), 1e-9);
        assertEquals(0.55, stored.getDefaultPreferenceWeight(), 1e-9);
        assertEquals("DK1", stored.getPriceArea());
        assertTrue(stored.getUser().getId().equals(savedUser.getId()));
    }
}