package com.school.app.platform;

import com.school.app.common.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformAuthServiceTest {

    @Mock
    private PlatformUserRepository platformUserRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private TotpService totpService;

    @InjectMocks
    private PlatformAuthService platformAuthService;

    private PlatformUser user;

    @BeforeEach
    void setUp() {
        user = PlatformUser.builder()
                .id(UUID.randomUUID())
                .name("Operator")
                .email("operator@school.app")
                .passwordHash("hashed")
                .platformRole(PlatformRole.PLATFORM_ADMIN)
                .build();
    }

    private void stubTokens() {
        when(jwtService.generatePlatformAccessToken(user.getEmail(), PlatformRole.PLATFORM_ADMIN))
                .thenReturn("platform-access");
        when(jwtService.generatePlatformRefreshToken(user.getEmail(), PlatformRole.PLATFORM_ADMIN))
                .thenReturn("platform-refresh");
    }

    @Test
    void loginSucceedsWithoutMfaWhenNotEnrolled() {
        when(platformUserRepository.findByEmail("operator@school.app")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Operator@123", "hashed")).thenReturn(true);
        stubTokens();

        PlatformAuthResponse response = platformAuthService.login(
                new PlatformLoginRequest("operator@school.app", "Operator@123", null));

        assertThat(response.accessToken()).isEqualTo("platform-access");
        assertThat(response.refreshToken()).isEqualTo("platform-refresh");
        assertThat(response.platformRole()).isEqualTo(PlatformRole.PLATFORM_ADMIN);
        assertThat(response.mfaEnrolled()).isFalse();
        verify(totpService, never()).verify(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void loginRejectsAnUnknownEmail() {
        when(platformUserRepository.findByEmail("nobody@school.app")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> platformAuthService.login(
                new PlatformLoginRequest("nobody@school.app", "whatever", null)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginRejectsAWrongPassword() {
        when(platformUserRepository.findByEmail("operator@school.app")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> platformAuthService.login(
                new PlatformLoginRequest("operator@school.app", "wrong", null)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginRequiresAValidMfaCodeOnceEnrolled() {
        user.setMfaSecret("TOTPSECRET");
        when(platformUserRepository.findByEmail("operator@school.app")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Operator@123", "hashed")).thenReturn(true);
        when(totpService.verify("TOTPSECRET", null)).thenReturn(false);

        assertThatThrownBy(() -> platformAuthService.login(
                new PlatformLoginRequest("operator@school.app", "Operator@123", null)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("MFA");
    }

    @Test
    void loginSucceedsWithAValidMfaCode() {
        user.setMfaSecret("TOTPSECRET");
        when(platformUserRepository.findByEmail("operator@school.app")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Operator@123", "hashed")).thenReturn(true);
        when(totpService.verify("TOTPSECRET", "123456")).thenReturn(true);
        stubTokens();

        PlatformAuthResponse response = platformAuthService.login(
                new PlatformLoginRequest("operator@school.app", "Operator@123", "123456"));

        assertThat(response.mfaEnrolled()).isTrue();
    }

    @Test
    void refreshRejectsATenantScopedToken() {
        when(jwtService.isPlatformToken("tenant-refresh")).thenReturn(false);

        assertThatThrownBy(() -> platformAuthService.refresh(new PlatformRefreshRequest("tenant-refresh")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refreshRejectsAPlatformAccessTokenPresentedAsRefresh() {
        when(jwtService.isPlatformToken("platform-access")).thenReturn(true);
        when(jwtService.isRefreshToken("platform-access")).thenReturn(false);

        assertThatThrownBy(() -> platformAuthService.refresh(new PlatformRefreshRequest("platform-access")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refreshReturnsANewTokenPairForAValidPlatformRefreshToken() {
        when(jwtService.isPlatformToken("valid-refresh")).thenReturn(true);
        when(jwtService.isRefreshToken("valid-refresh")).thenReturn(true);
        when(jwtService.extractUsername("valid-refresh")).thenReturn("operator@school.app");
        when(platformUserRepository.findByEmail("operator@school.app")).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid("valid-refresh", user)).thenReturn(true);
        stubTokens();

        PlatformAuthResponse response = platformAuthService.refresh(new PlatformRefreshRequest("valid-refresh"));

        assertThat(response.accessToken()).isEqualTo("platform-access");
        assertThat(response.refreshToken()).isEqualTo("platform-refresh");
    }

    @Test
    void refreshRejectsAnExpiredToken() {
        when(jwtService.isPlatformToken("expired")).thenReturn(true);
        when(jwtService.isRefreshToken("expired")).thenReturn(true);
        when(jwtService.extractUsername("expired")).thenReturn("operator@school.app");
        when(platformUserRepository.findByEmail("operator@school.app")).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid("expired", user)).thenReturn(false);

        assertThatThrownBy(() -> platformAuthService.refresh(new PlatformRefreshRequest("expired")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void enrollMfaReturnsACandidateSecretWithoutPersistingIt() {
        when(totpService.generateSecret()).thenReturn("NEWSECRET");
        when(totpService.otpAuthUri("NEWSECRET", "operator@school.app")).thenReturn("otpauth://totp/x");

        MfaEnrollResponse response = platformAuthService.enrollMfa(user);

        assertThat(response.secret()).isEqualTo("NEWSECRET");
        assertThat(response.otpAuthUri()).isEqualTo("otpauth://totp/x");
        verify(platformUserRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void confirmMfaPersistsTheSecretOnlyAfterAValidCode() {
        when(totpService.verify("NEWSECRET", "654321")).thenReturn(true);
        when(platformUserRepository.findById(user.getId())).thenReturn(Optional.of(user));

        platformAuthService.confirmMfa(user, new MfaConfirmRequest("NEWSECRET", "654321"));

        assertThat(user.getMfaSecret()).isEqualTo("NEWSECRET");
        verify(platformUserRepository).save(user);
    }

    @Test
    void confirmMfaRejectsAnInvalidCodeWithoutPersistingAnything() {
        when(totpService.verify("NEWSECRET", "000000")).thenReturn(false);

        assertThatThrownBy(() -> platformAuthService.confirmMfa(user, new MfaConfirmRequest("NEWSECRET", "000000")))
                .isInstanceOf(BadCredentialsException.class);
        verify(platformUserRepository, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(user.getMfaSecret()).isNull();
    }
}
