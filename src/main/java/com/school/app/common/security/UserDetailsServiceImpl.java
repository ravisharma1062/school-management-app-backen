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
     * can't be set yet (finding it is the point of this query), this calls a server-side function
     * (V26) that sets the narrow {@code app.pre_auth_lookup} exception, performs the lookup, and
     * resets it — entirely within one atomic statement, so it doesn't depend on this connection's
     * surrounding transaction (e.g. open-in-view) behaving any particular way across statements.
     */
    private UUID resolveSchoolIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT resolve_login_school_id(?)", UUID.class, email);
    }
}
