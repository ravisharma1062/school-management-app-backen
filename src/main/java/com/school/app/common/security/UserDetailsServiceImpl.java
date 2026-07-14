package com.school.app.common.security;

import com.school.app.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
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
        // tenant first via a query that bypasses the Hibernate filter. Emails are globally
        // unique, so this is safe.
        if (!TenantContext.isSet()) {
            UUID schoolId = resolveSchoolIdByEmail(email);
            if (schoolId == null) {
                throw new UsernameNotFoundException("No user found with email " + email);
            }
            TenantContext.set(schoolId);
        }

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No user found with email " + email));
    }

    /**
     * Bypassing Hibernate's {@code @TenantId} filter with plain JDBC still isn't enough on its
     * own — {@code users} also has forced Postgres row-level security (V17), which every query on
     * that connection must satisfy regardless of how it's issued. Since {@code app.current_school_id}
     * can't be set yet (finding it is the point of this query), this opens the narrow
     * {@code app.pre_auth_lookup} exception from V25 for exactly this one hardcoded statement,
     * then resets it before the pooled connection can be reused by another request.
     */
    private UUID resolveSchoolIdByEmail(String email) {
        return jdbcTemplate.execute((ConnectionCallback<UUID>) connection -> {
            try (Statement enable = connection.createStatement()) {
                enable.execute("SET app.pre_auth_lookup = 'true'");
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT school_id FROM users WHERE email = ?")) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? (UUID) rs.getObject("school_id") : null;
                }
            } finally {
                try (Statement disable = connection.createStatement()) {
                    disable.execute("SET app.pre_auth_lookup = 'false'");
                }
            }
        });
    }
}
