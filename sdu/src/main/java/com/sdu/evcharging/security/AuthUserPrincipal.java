package com.sdu.evcharging.security;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.sdu.evcharging.domain.User;

public record AuthUserPrincipal(
        Long userId,
        String email,
        String passwordHash,
        String role
) implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    public static AuthUserPrincipal fromUser(User user) {
        return new AuthUserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getRole().name()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
