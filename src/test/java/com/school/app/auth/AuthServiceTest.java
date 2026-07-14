package com.school.app.auth;

import com.school.app.common.security.JwtService;
import com.school.app.user.Role;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private User user;
    private UUID schoolId;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        user = User.builder()
                .id(UUID.randomUUID())
                .schoolId(schoolId)
                .name("Admin")
                .email("admin@school.app")
                .passwordHash("hashed")
                .role(Role.ADMIN)
                .build();
    }

    @Test
    void loginReturnsTokensAndRoleOnSuccess() {
        LoginRequest request = new LoginRequest("admin@school.app", "correct-password");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(user);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtService.generateAccessToken(user, schoolId)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(user, schoolId)).thenReturn("refresh-token");

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.role()).isEqualTo(Role.ADMIN);
        verify(authenticationManager).authenticate(any());
    }

    @Test
    void loginPropagatesBadCredentialsWhenAuthenticationFails() {
        LoginRequest request = new LoginRequest("admin@school.app", "wrong-password");
        doThrow(new BadCredentialsException("bad creds")).when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refreshRejectsAnAccessTokenPresentedAsRefresh() {
        RefreshRequest request = new RefreshRequest("some-access-token");
        when(jwtService.isRefreshToken("some-access-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refreshReturnsNewTokenPairForValidRefreshToken() {
        RefreshRequest request = new RefreshRequest("valid-refresh-token");
        when(jwtService.isRefreshToken("valid-refresh-token")).thenReturn(true);
        when(jwtService.extractSchoolId("valid-refresh-token")).thenReturn(schoolId);
        when(jwtService.extractUsername("valid-refresh-token")).thenReturn("admin@school.app");
        when(userRepository.findByEmail("admin@school.app")).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid("valid-refresh-token", user)).thenReturn(true);
        when(jwtService.generateAccessToken(user, schoolId)).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(user, schoolId)).thenReturn("new-refresh-token");

        AuthResponse response = authService.refresh(request);

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
    }
}
