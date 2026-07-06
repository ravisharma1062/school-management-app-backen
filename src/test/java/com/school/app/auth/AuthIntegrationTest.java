package com.school.app.auth;

import com.school.app.common.AbstractIntegrationTest;
import com.school.app.user.UserDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends AbstractIntegrationTest {

    private static final String SEEDED_ADMIN_EMAIL = "admin@school.app";
    private static final String SEEDED_ADMIN_PASSWORD = "Admin@123";

    @Test
    void loginSucceedsWithSeededAdminCredentials() {
        LoginRequest request = new LoginRequest(SEEDED_ADMIN_EMAIL, SEEDED_ADMIN_PASSWORD);

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
    }

    @Test
    void loginFailsWithWrongPasswordReturns401() {
        LoginRequest request = new LoginRequest(SEEDED_ADMIN_EMAIL, "wrong-password");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedRouteWithoutTokenReturns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/auth/me", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void meReturnsCurrentUserProfileWhenAuthenticated() {
        LoginRequest loginRequest = new LoginRequest(SEEDED_ADMIN_EMAIL, SEEDED_ADMIN_PASSWORD);
        AuthResponse tokens = restTemplate.postForObject("/api/v1/auth/login", loginRequest, AuthResponse.class);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.accessToken());

        ResponseEntity<UserDto> response = restTemplate.exchange(
                "/api/v1/auth/me", org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().email()).isEqualTo(SEEDED_ADMIN_EMAIL);
    }
}
