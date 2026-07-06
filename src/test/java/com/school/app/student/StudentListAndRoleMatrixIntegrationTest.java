package com.school.app.student;

import com.school.app.attendance.AttendanceDto;
import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import com.school.app.examresult.ExamResultCreateRequest;
import com.school.app.user.Role;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Closes remaining gaps in the per-endpoint role access matrix that aren't
 * already covered by each feature's own integration test class.
 */
class StudentListAndRoleMatrixIntegrationTest extends AbstractIntegrationTest {

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
    void adminAndTeacherCanListStudentsButParentCannot() {
        ResponseEntity<String> adminResponse = restTemplate.exchange(
                "/api/v1/students?page=0&size=10", HttpMethod.GET, new HttpEntity<>(adminHeaders()), String.class);
        assertThat(adminResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> teacherResponse = restTemplate.exchange(
                "/api/v1/students?page=0&size=10", HttpMethod.GET, new HttpEntity<>(authHeaders(teacher)), String.class);
        assertThat(teacherResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> parentResponse = restTemplate.exchange(
                "/api/v1/students?page=0&size=10", HttpMethod.GET, new HttpEntity<>(authHeaders(parent)), String.class);
        assertThat(parentResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void teacherCanViewAttendanceByClassSectionDateButParentCannot() {
        String path = "/api/v1/attendance/class/8/A/" + LocalDate.now();

        ResponseEntity<AttendanceDto[]> teacherResponse = restTemplate.exchange(
                path, HttpMethod.GET, new HttpEntity<>(authHeaders(teacher)), AttendanceDto[].class);
        assertThat(teacherResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> parentResponse = restTemplate.exchange(
                path, HttpMethod.GET, new HttpEntity<>(authHeaders(parent)), String.class);
        assertThat(parentResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanRecordExamResultButParentCannot() {
        // Grade computation requires a real student; a not-found student is still
        // sufficient here since the role check runs via @PreAuthorize before the
        // service looks the student up, so a PARENT caller is rejected with 403
        // rather than a 404, proving the access-control layer is enforced first.
        ExamResultCreateRequest request = new ExamResultCreateRequest(
                UUID.randomUUID(), "Math", "Final", new BigDecimal("70"), new BigDecimal("100"), "Term 2");

        HttpHeaders adminHeaders = adminHeaders();
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> adminResponse = restTemplate.exchange(
                "/api/v1/exam-results", HttpMethod.POST, new HttpEntity<>(request, adminHeaders), String.class);
        assertThat(adminResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        HttpHeaders parentHeaders = authHeaders(parent);
        parentHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> parentResponse = restTemplate.exchange(
                "/api/v1/exam-results", HttpMethod.POST, new HttpEntity<>(request, parentHeaders), String.class);
        assertThat(parentResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
