package com.sdu.evcharging.service.auth;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sdu.evcharging.domain.UserConstraints;
import com.sdu.evcharging.dto.auth.UpdateUserConstraintsRequest;
import com.sdu.evcharging.repository.UserConstraintsRepository;

@ExtendWith(MockitoExtension.class)
class UserConstraintsServiceTests {

    @Mock
    private UserConstraintsRepository userConstraintsRepository;

    private UserConstraintsService userConstraintsService;

    @BeforeEach
    void setUp() {
        userConstraintsService = new UserConstraintsService(userConstraintsRepository);
    }

    @Test
    void getByUserId_ReturnsStoredConstraints() {
        UserConstraints constraints = UserConstraints.builder()
                .defaultBatteryCapacity(77.0)
                .defaultMaxPower(11.0)
                .defaultPreferenceWeight(0.65)
                .priceArea("DK2")
                .build();
        when(userConstraintsRepository.findByUserId(42L)).thenReturn(Optional.of(constraints));

        var response = userConstraintsService.getByUserId(42L);

        assertEquals(77.0, response.defaultBatteryCapacity(), 1e-9);
        assertEquals(11.0, response.defaultMaxPower(), 1e-9);
        assertEquals(0.65, response.defaultPreferenceWeight(), 1e-9);
        assertEquals("DK2", response.priceArea());
    }

    @Test
    void getPriceAreaByUserId_NormalizesStoredZone() {
        UserConstraints constraints = UserConstraints.builder()
                .defaultBatteryCapacity(77.0)
                .defaultMaxPower(11.0)
                .defaultPreferenceWeight(0.65)
                .priceArea("dk1")
                .build();
        when(userConstraintsRepository.findByUserId(42L)).thenReturn(Optional.of(constraints));

        String priceArea = userConstraintsService.getPriceAreaByUserId(42L);

        assertEquals("DK1", priceArea);
    }

    @Test
    void updateByUserId_RejectsInvalidPreferenceWeight() {
        UpdateUserConstraintsRequest request = new UpdateUserConstraintsRequest(77.0, 11.0, 1.2, "DK1");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userConstraintsService.updateByUserId(42L, request));

        assertEquals("Preference weight must be between 0 and 1", exception.getMessage());
    }

    @Test
    void updateByUserId_NormalizesZoneAndPersistsChanges() {
        UserConstraints constraints = UserConstraints.builder()
                .defaultBatteryCapacity(50.0)
                .defaultMaxPower(7.0)
                .defaultPreferenceWeight(0.4)
                .priceArea("DK2")
                .build();
        when(userConstraintsRepository.findByUserId(42L)).thenReturn(Optional.of(constraints));
        when(userConstraintsRepository.save(any(UserConstraints.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateUserConstraintsRequest request = new UpdateUserConstraintsRequest(85.0, 22.0, 0.75, "dk1");
        var response = userConstraintsService.updateByUserId(42L, request);

        ArgumentCaptor<UserConstraints> captor = ArgumentCaptor.forClass(UserConstraints.class);
        verify(userConstraintsRepository).save(captor.capture());

        UserConstraints saved = captor.getValue();
        assertEquals(85.0, saved.getDefaultBatteryCapacity(), 1e-9);
        assertEquals(22.0, saved.getDefaultMaxPower(), 1e-9);
        assertEquals(0.75, saved.getDefaultPreferenceWeight(), 1e-9);
        assertEquals("DK1", saved.getPriceArea());

        assertEquals(85.0, response.defaultBatteryCapacity(), 1e-9);
        assertEquals(22.0, response.defaultMaxPower(), 1e-9);
        assertEquals(0.75, response.defaultPreferenceWeight(), 1e-9);
        assertEquals("DK1", response.priceArea());
    }
}