package com.school.app.common.security;

import com.school.app.user.Role;
import com.school.app.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha256-signing";

    private JwtService jwtService;
    private User user;
    private UUID schoolId;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 60_000L, 3_600_000L);
        schoolId = UUID.randomUUID();
        user = User.builder()
                .id(UUID.randomUUID())
                .schoolId(schoolId)
                .name("Jane Teacher")
                .email("jane@school.app")
                .passwordHash("hashed")
                .role(Role.TEACHER)
                .build();
    }

    @Test
    void generatesAccessTokenThatIsValidForTheSameUser() {
        String token = jwtService.generateAccessToken(user, schoolId);

        assertThat(jwtService.extractUsername(token)).isEqualTo(user.getEmail());
        assertThat(jwtService.isTokenValid(token, user)).isTrue();
        assertThat(jwtService.isRefreshToken(token)).isFalse();
        assertThat(jwtService.extractSchoolId(token)).isEqualTo(schoolId);
    }

    @Test
    void generatesRefreshTokenMarkedAsRefreshType() {
        String token = jwtService.generateRefreshToken(user, schoolId);

        assertThat(jwtService.isRefreshToken(token)).isTrue();
        assertThat(jwtService.isTokenValid(token, user)).isTrue();
        assertThat(jwtService.extractSchoolId(token)).isEqualTo(schoolId);
    }

    @Test
    void tokenIsInvalidForADifferentUser() {
        String token = jwtService.generateAccessToken(user, schoolId);

        User otherUser = User.builder()
                .id(UUID.randomUUID())
                .name("Other")
                .email("other@school.app")
                .passwordHash("hashed")
                .role(Role.PARENT)
                .build();

        assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
    }

    @Test
    void malformedTokenIsRejectedWithoutThrowing() {
        assertThat(jwtService.isTokenValid("not-a-real-token", user)).isFalse();
    }
}
