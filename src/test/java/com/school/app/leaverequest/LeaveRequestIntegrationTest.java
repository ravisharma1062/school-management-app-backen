package com.school.app.leaverequest;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LeaveRequestIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "Password@123";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User teacher;
    private User otherTeacher;
    private User parent;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        teacher = userRepository.save(User.builder()
                .name("Teacher " + suffix)
                .email("teacher-lr-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.TEACHER)
                .build());

        otherTeacher = userRepository.save(User.builder()
                .name("Other Teacher " + suffix)
                .email("other-teacher-lr-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.TEACHER)
                .build());

        parent = userRepository.save(User.builder()
                .name("Parent " + suffix)
                .email("parent-lr-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.PARENT)
                .build());
    }

    @Test
    void teacherCanSubmitAndSeeOwnLeaveRequest() {
        LeaveRequestCreateRequest request = new LeaveRequestCreateRequest(
                LeaveType.SICK, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2), "Flu");

        ResponseEntity<LeaveRequestDto> createResponse = post(request, authHeaders(teacher));
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getBody().status()).isEqualTo(LeaveStatus.PENDING);
        assertThat(createResponse.getBody().requesterId()).isEqualTo(teacher.getId());

        ResponseEntity<String> listResponse = restTemplate.exchange(
                "/api/v1/leave-requests", HttpMethod.GET, new HttpEntity<>(authHeaders(teacher)), String.class);
        assertThat(listResponse.getBody()).contains(createResponse.getBody().id().toString());
    }

    @Test
    void toDateBeforeFromDateIsRejected() {
        LeaveRequestCreateRequest request = new LeaveRequestCreateRequest(
                LeaveType.CASUAL, LocalDate.now().plusDays(5), LocalDate.now().plusDays(1), null);

        ResponseEntity<String> response = post(request, authHeaders(teacher), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void teacherCannotSeeAnotherTeachersLeaveRequestInTheirOwnList() {
        LeaveRequestCreateRequest request = new LeaveRequestCreateRequest(
                LeaveType.CASUAL, LocalDate.now().plusDays(1), LocalDate.now().plusDays(1), null);
        ResponseEntity<LeaveRequestDto> createResponse = post(request, authHeaders(teacher));

        ResponseEntity<String> otherTeacherList = restTemplate.exchange(
                "/api/v1/leave-requests", HttpMethod.GET, new HttpEntity<>(authHeaders(otherTeacher)), String.class);

        assertThat(otherTeacherList.getBody()).isNotNull();
        assertThat(otherTeacherList.getBody()).doesNotContain(createResponse.getBody().id().toString());
    }

    @Test
    void adminCanApproveAndSeesAllRequests() {
        LeaveRequestCreateRequest request = new LeaveRequestCreateRequest(
                LeaveType.OTHER, LocalDate.now().plusDays(3), LocalDate.now().plusDays(3), "Family event");
        ResponseEntity<LeaveRequestDto> createResponse = post(request, authHeaders(parent));
        UUID id = createResponse.getBody().id();

        LeaveRequestReviewRequest review = new LeaveRequestReviewRequest(LeaveStatus.APPROVED);
        HttpHeaders adminHeaders = adminHeaders();
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<LeaveRequestDto> reviewResponse = restTemplate.exchange(
                "/api/v1/leave-requests/" + id, HttpMethod.PATCH,
                new HttpEntity<>(review, adminHeaders), LeaveRequestDto.class);

        assertThat(reviewResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reviewResponse.getBody()).isNotNull();
        assertThat(reviewResponse.getBody().status()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(reviewResponse.getBody().reviewedBy()).isNotNull();

        ResponseEntity<String> adminList = restTemplate.exchange(
                "/api/v1/leave-requests?status=APPROVED", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);
        assertThat(adminList.getBody()).contains(id.toString());
    }

    @Test
    void teacherCannotReviewLeaveRequests() {
        LeaveRequestCreateRequest request = new LeaveRequestCreateRequest(
                LeaveType.SICK, LocalDate.now(), LocalDate.now(), null);
        ResponseEntity<LeaveRequestDto> createResponse = post(request, authHeaders(teacher));

        LeaveRequestReviewRequest review = new LeaveRequestReviewRequest(LeaveStatus.APPROVED);
        HttpHeaders headers = authHeaders(teacher);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/leave-requests/" + createResponse.getBody().id(), HttpMethod.PATCH,
                new HttpEntity<>(review, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<LeaveRequestDto> post(LeaveRequestCreateRequest request, HttpHeaders headers) {
        return post(request, headers, LeaveRequestDto.class);
    }

    private <T> ResponseEntity<T> post(LeaveRequestCreateRequest request, HttpHeaders headers, Class<T> responseType) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                "/api/v1/leave-requests", HttpMethod.POST, new HttpEntity<>(request, headers), responseType);
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
