package com.school.app.platform;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Deliberately NOT a Spring Security {@code UserDetailsService} — that interface already has one
 * implementation ({@code UserDetailsServiceImpl}, for tenant {@code User}s) registered as the sole
 * bean {@link com.school.app.common.config.SecurityConfig} wires into the global
 * {@code AuthenticationManager}. Platform login/token-validation never goes through that manager
 * (see {@link PlatformAuthService}, {@link com.school.app.common.security.JwtAuthFilter}), so this
 * stays a plain lookup used directly by the two call sites that need it.
 */
@Service
@RequiredArgsConstructor
public class PlatformUserLookupService {

    private final PlatformUserRepository platformUserRepository;

    public PlatformUser loadByEmail(String email) {
        return platformUserRepository.findByEmail(email).orElse(null);
    }
}
