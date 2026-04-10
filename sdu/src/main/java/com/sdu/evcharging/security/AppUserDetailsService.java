package com.sdu.evcharging.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.sdu.evcharging.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmail(normalize(username))
                .map(AuthUserPrincipal::fromUser)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private String normalize(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase();
    }
}
