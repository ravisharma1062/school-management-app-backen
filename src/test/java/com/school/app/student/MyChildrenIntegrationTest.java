package com.school.app.student;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import com.school.app.user.Role;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers GET /api/v1/students/my-children: a parent sees only their own linked
 * children, and non-parent roles are denied.
 */
class MyChildrenIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "Password@123";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User parent;
    private User otherParent;
    private User teacher;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        parent = userRepository.save(User.builder()
                .name("Parent " + suffix)
                .email("parent-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.PARENT)
                .build());

        otherParent = userRepository.save(User.builder()
                .name("Other Parent " + suffix)
                .email("other-parent-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.PARENT)
                .build());

        teacher = userRepository.save(User.builder()
                .name("Teacher " + suffix)
                .email("teacher-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.TEACHER)
                .build());

        studentRepository.save(Student.builder()
                .name("My Child A " + suffix)
                .rollNo("MC-A-" + suffix)
                .studentClass("5").section("A")
                .dob(LocalDate.of(2015, 3, 1))
                .parent(parent)
                .build());
        studentRepository.save(Student.builder()
                .name("My Child B " + suffix)
                .rollNo("MC-B-" + suffix)
                .studentClass("7").section("B")
                .dob(LocalDate.of(2013, 6, 12))
                .parent(parent)
                .build());
        // Belongs to a different parent — must not appear.
        studentRepository.save(Student.builder()
                .name("Not Mine " + suffix)
                .rollNo("NM-" + suffix)
                .studentClass("5").section("A")
                .dob(LocalDate.of(2015, 1, 1))
                .parent(otherParent)
                .build());
    }

    private HttpHeaders authHeaders(User user) {
        LoginRequest request = new LoginRequest(user.getEmail(), TEST_PASSWORD);
        AuthResponse response = restTemplate.postForObject("/api/v1/auth/login", request, AuthResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(response.accessToken());
        return headers;
    }

    @Test
    void parentSeesOnlyTheirOwnChildren() {
        ResponseEntity<StudentDto[]> response = restTemplate.exchange(
                "/api/v1/students/my-children", HttpMethod.GET,
                new HttpEntity<>(authHeaders(parent)), StudentDto[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody())
                .hasSize(2)
                .allSatisfy(child -> assertThat(child.parentId()).isEqualTo(parent.getId()));
    }

    @Test
    void teacherCannotAccessMyChildren() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/students/my-children", HttpMethod.GET,
                new HttpEntity<>(authHeaders(teacher)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
