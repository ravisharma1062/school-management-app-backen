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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StudentArchiveIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "Password@123";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User teacher;
    private User parent;
    private Student child;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        teacher = userRepository.save(User.builder()
                .name("Teacher " + suffix)
                .email("teacher-arch-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.TEACHER)
                .build());

        parent = userRepository.save(User.builder()
                .name("Parent " + suffix)
                .email("parent-arch-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.PARENT)
                .build());

        child = studentRepository.save(Student.builder()
                .name("Archivable Child " + suffix)
                .rollNo("ARC-" + suffix)
                .studentClass("9")
                .section("Z")
                .dob(LocalDate.of(2013, 1, 1))
                .parent(parent)
                .build());
    }

    @Test
    void adminCanArchiveAndRestoreStudent() {
        HttpHeaders headers = adminHeaders();

        ResponseEntity<StudentDto> archiveResponse = restTemplate.exchange(
                "/api/v1/students/" + child.getId() + "/archive", HttpMethod.PATCH,
                new HttpEntity<>(headers), StudentDto.class);
        assertThat(archiveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(archiveResponse.getBody()).isNotNull();
        assertThat(archiveResponse.getBody().active()).isFalse();

        ResponseEntity<StudentDto> restoreResponse = restTemplate.exchange(
                "/api/v1/students/" + child.getId() + "/restore", HttpMethod.PATCH,
                new HttpEntity<>(headers), StudentDto.class);
        assertThat(restoreResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restoreResponse.getBody()).isNotNull();
        assertThat(restoreResponse.getBody().active()).isTrue();
    }

    @Test
    void teacherCannotArchiveStudent() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/students/" + child.getId() + "/archive", HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(teacher)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void archivedStudentExcludedFromDefaultListButIncludedWithIncludeArchived() {
        HttpHeaders headers = adminHeaders();
        restTemplate.exchange(
                "/api/v1/students/" + child.getId() + "/archive", HttpMethod.PATCH,
                new HttpEntity<>(headers), StudentDto.class);

        ResponseEntity<String> defaultList = restTemplate.exchange(
                "/api/v1/students?size=200", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(defaultList.getBody()).isNotNull();
        assertThat(defaultList.getBody()).doesNotContain(child.getId().toString());

        ResponseEntity<String> fullList = restTemplate.exchange(
                "/api/v1/students?size=200&includeArchived=true", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(fullList.getBody()).isNotNull();
        assertThat(fullList.getBody()).contains(child.getId().toString());
    }

    @Test
    void archivedChildExcludedFromParentsMyChildren() {
        HttpHeaders headers = adminHeaders();
        restTemplate.exchange(
                "/api/v1/students/" + child.getId() + "/archive", HttpMethod.PATCH,
                new HttpEntity<>(headers), StudentDto.class);

        ResponseEntity<StudentDto[]> response = restTemplate.exchange(
                "/api/v1/students/my-children", HttpMethod.GET,
                new HttpEntity<>(authHeaders(parent)), StudentDto[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(List.of(response.getBody())).extracting(StudentDto::id).doesNotContain(child.getId());
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
}
