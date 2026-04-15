package com.sdu.evcharging.service.auth;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.sdu.evcharging.domain.User;
import com.sdu.evcharging.domain.UserRole;
import com.sdu.evcharging.dto.auth.LoginRequest;
import com.sdu.evcharging.dto.auth.SignupRequest;
import com.sdu.evcharging.repository.UserRepository;
import com.sdu.evcharging.security.JwtProvider;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(jwtProvider.generateToken(any())).thenReturn("token");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void signup_AssignsAdminRoleToConfiguredEmail() {
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded");

        authService.signup(new SignupRequest("admin@example.com", "TestPass123!"));

        verify(userRepository).save(userCaptor.capture());
        assertEquals(UserRole.ADMIN, userCaptor.getValue().getRole());
    }

    @Test
    void signup_AssignsUserRoleToOtherEmails() {
        when(userRepository.existsByEmail("other@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded");

        authService.signup(new SignupRequest("other@example.com", "TestPass123!"));

        verify(userRepository).save(userCaptor.capture());
        assertEquals(UserRole.USER, userCaptor.getValue().getRole());
    }

    @Test
    void login_DemotesNonAdminEmailIfStoredAsAdmin() {
        User persisted = User.builder()
                .id(7L)
                .email("regular@example.com")
                .passwordHash("encoded")
                .role(UserRole.ADMIN)
                .build();

        when(userRepository.findByEmail("regular@example.com")).thenReturn(Optional.of(persisted));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);

        authService.login(new LoginRequest("regular@example.com", "TestPass123!"));

        verify(userRepository).save(userCaptor.capture());
        assertEquals(UserRole.USER, userCaptor.getValue().getRole());
    }
}
