package com.school.app.common.security;

import com.school.app.user.Role;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private TenantRlsTransactionListener tenantRlsTransactionListener;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void withNoTenantSetResolvesSchoolIdViaJdbcThenLoadsBypassingTheFilter() {
        UUID schoolId = UUID.randomUUID();
        User user = User.builder().id(UUID.randomUUID()).schoolId(schoolId).name("Admin")
                .email("admin@school.app").passwordHash("hashed").role(Role.ADMIN).build();
        when(jdbcTemplate.queryForObject(eq("SELECT resolve_login_school_id(?)"), eq(UUID.class), eq("admin@school.app")))
                .thenReturn(schoolId);
        when(userRepository.findByEmailBypassingTenantFilter("admin@school.app")).thenReturn(Optional.of(user));

        UserDetails result = userDetailsService.loadUserByUsername("admin@school.app");

        assertThat(result).isEqualTo(user);
        assertThat(TenantContext.get()).isEqualTo(schoolId);
        verify(tenantRlsTransactionListener).applyCurrentTenant(entityManager);
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void withNoTenantSetAndUnknownEmailThrowsWithoutLeakingWhichPartFailed() {
        when(jdbcTemplate.queryForObject(eq("SELECT resolve_login_school_id(?)"), eq(UUID.class), eq("nobody@school.app")))
                .thenReturn(null);

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nobody@school.app"))
                .isInstanceOf(UsernameNotFoundException.class);
        verify(userRepository, never()).findByEmailBypassingTenantFilter(any());
    }

    @Test
    void withNoTenantSetAndResolvedSchoolButNoUserRowThrows() {
        UUID schoolId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(eq("SELECT resolve_login_school_id(?)"), eq(UUID.class), eq("ghost@school.app")))
                .thenReturn(schoolId);
        when(userRepository.findByEmailBypassingTenantFilter("ghost@school.app")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("ghost@school.app"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void withATenantAlreadySetUsesThePlainTenantScopedLookup() {
        UUID schoolId = UUID.randomUUID();
        TenantContext.set(schoolId);
        User user = User.builder().id(UUID.randomUUID()).schoolId(schoolId).name("Teacher")
                .email("teacher@school.app").passwordHash("hashed").role(Role.TEACHER).build();
        when(userRepository.findByEmail("teacher@school.app")).thenReturn(Optional.of(user));

        UserDetails result = userDetailsService.loadUserByUsername("teacher@school.app");

        assertThat(result).isEqualTo(user);
        verify(jdbcTemplate, never()).queryForObject(any(String.class), eq(UUID.class), any());
        verify(tenantRlsTransactionListener, never()).applyCurrentTenant(any());
    }

    @Test
    void withATenantAlreadySetAndUnknownEmailThrows() {
        TenantContext.set(UUID.randomUUID());
        when(userRepository.findByEmail("nobody@school.app")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nobody@school.app"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
