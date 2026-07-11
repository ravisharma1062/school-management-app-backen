package com.school.app.user;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "Password@123";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User teacher;
    private User parent;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        teacher = userRepository.save(User.builder()
                .name("Teacher " + suffix)
                .email("teacher-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.TEACHER)
                .build());

        parent = userRepository.save(User.builder()
                .name("Parent " + suffix)
                .email("parent-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.PARENT)
                .build());
    }

    private HttpHeaders authHeaders(User user) {
        LoginRequest request = new LoginRequest(user.getEmail(), TEST_PASSWORD);
        AuthResponse response = restTemplate.postForObject("/api/v1/auth/login", request, AuthResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(response.accessToken());
        return headers;
    }

    private HttpHeaders adminHeaders() {
        LoginRequest request = new LoginRequest("admin@school.app", "Admin@123");
        AuthResponse response = restTemplate.postForObject("/api/v1/auth/login", request, AuthResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(response.accessToken());
        return headers;
    }

    @Test
    void adminCanCreateUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserCreateRequest request = new UserCreateRequest(
                "New Teacher", "new-teacher-" + suffix + "@school.app", "Password@123", Role.TEACHER, "555-1234");

        HttpHeaders headers = adminHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/users", HttpMethod.POST, new HttpEntity<>(request, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("name")).isEqualTo("New Teacher");
        assertThat(response.getBody().get("role")).isEqualTo("TEACHER");
        assertThat(response.getBody()).doesNotContainKey("password");
        assertThat(response.getBody()).doesNotContainKey("passwordHash");
    }

    @Test
    void creatingUserWithDuplicateEmailReturnsConflict() {
        UserCreateRequest request = new UserCreateRequest(
                "Duplicate Teacher", teacher.getEmail(), "Password@123", Role.TEACHER, null);

        HttpHeaders headers = adminHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/users", HttpMethod.POST, new HttpEntity<>(request, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(409);
        assertThat(response.getBody().get("message")).asString().contains(teacher.getEmail());
    }

    @Test
    void teacherCannotCreateUser() {
        UserCreateRequest request = new UserCreateRequest(
                "Blocked User", "blocked-" + UUID.randomUUID() + "@school.app", "Password@123", Role.PARENT, null);

        HttpHeaders headers = authHeaders(teacher);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users", HttpMethod.POST, new HttpEntity<>(request, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void parentCannotCreateUser() {
        UserCreateRequest request = new UserCreateRequest(
                "Blocked User", "blocked2-" + UUID.randomUUID() + "@school.app", "Password@123", Role.PARENT, null);

        HttpHeaders headers = authHeaders(parent);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users", HttpMethod.POST, new HttpEntity<>(request, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanListUsersFilteredByRole() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/users?role=TEACHER&page=0&size=50", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).isNotEmpty();
        assertThat(content).allSatisfy(u -> assertThat(u.get("role")).isEqualTo("TEACHER"));
    }

    @Test
    void adminCanListAllUsersPaginated() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/users?page=0&size=50", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        List<String> roles = content.stream().map(u -> (String) u.get("role")).distinct().toList();
        assertThat(roles).contains("ADMIN", "TEACHER", "PARENT");
    }

    @Test
    void teacherCannotListUsers() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users", HttpMethod.GET, new HttpEntity<>(authHeaders(teacher)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void parentCannotListUsers() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users", HttpMethod.GET, new HttpEntity<>(authHeaders(parent)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void newUsersDefaultToEnglishAndCanSwitchToHindi() {
        HttpHeaders headers = authHeaders(parent);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> meBefore = restTemplate.exchange(
                "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(meBefore.getBody().get("preferredLanguage")).isEqualTo("EN");

        ResponseEntity<Map> updated = restTemplate.exchange(
                "/api/v1/users/me/language", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("preferredLanguage", "HI"), headers), Map.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("preferredLanguage")).isEqualTo("HI");

        ResponseEntity<Map> meAfter = restTemplate.exchange(
                "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(meAfter.getBody().get("preferredLanguage")).isEqualTo("HI");
    }
}
