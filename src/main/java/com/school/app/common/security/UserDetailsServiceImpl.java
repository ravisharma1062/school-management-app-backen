package com.school.app.common.security;

import com.school.app.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // users is now @TenantId-filtered, so a plain JPA lookup returns nothing until the
        // tenant is known. JwtAuthFilter already sets TenantContext from the validated JWT's
        // school_id claim for normal requests, but at login there is no JWT yet — resolve the
        // tenant first via a query that bypasses the Hibernate filter entirely (plain JDBC,
        // not routed through the entity manager). Emails are globally unique, so this is safe.
        if (!TenantContext.isSet()) {
            UUID schoolId = jdbcTemplate.query(
                    "SELECT school_id FROM users WHERE email = ?",
                    rs -> rs.next() ? (UUID) rs.getObject("school_id") : null,
                    email);
            if (schoolId == null) {
                throw new UsernameNotFoundException("No user found with email " + email);
            }
            TenantContext.set(schoolId);
        }

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No user found with email " + email));
    }
}
