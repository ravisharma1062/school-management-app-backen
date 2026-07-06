package com.school.app.notice;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import com.school.app.fee.Fee;
import com.school.app.fee.FeeDto;
import com.school.app.fee.FeeRepository;
import com.school.app.fee.FeeStatus;
import com.school.app.fee.FeeUpdateRequest;
import com.school.app.student.Student;
import com.school.app.student.StudentRepository;
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

class NoticeFeeIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "Password@123";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private FeeRepository feeRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User parent;
    private User otherParent;
    private Student child;
    private Fee fee;

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

        child = studentRepository.save(Student.builder()
                .name("Child " + suffix)
                .rollNo("R-" + suffix)
                .studentClass("9")
                .section("A")
                .dob(LocalDate.of(2011, 1, 1))
                .parent(parent)
                .build());

        fee = feeRepository.save(Fee.builder()
                .student(child)
                .term("Term 1")
                .amountDue(new BigDecimal("1000.00"))
                .amountPaid(BigDecimal.ZERO)
                .status(FeeStatus.PENDING)
                .dueDate(LocalDate.now().plusDays(30))
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
    void adminCanPostNoticeAndParentCanSeeItWhenTargetedAtAll() {
        NoticeCreateRequest request = new NoticeCreateRequest("Holiday", "School closed Friday", TargetRole.ALL);

        HttpHeaders adminHeaders = adminHeaders();
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<NoticeDto> createResponse = restTemplate.exchange(
                "/api/v1/notices", HttpMethod.POST, new HttpEntity<>(request, adminHeaders), NoticeDto.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> parentResponse = restTemplate.exchange(
                "/api/v1/notices", HttpMethod.GET, new HttpEntity<>(authHeaders(parent)), String.class);
        assertThat(parentResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void teacherCannotPostNotice() {
        NoticeCreateRequest request = new NoticeCreateRequest("Blocked", "desc", TargetRole.ALL);

        User teacher = userRepository.save(User.builder()
                .name("Teacher")
                .email("teacher-notice-" + UUID.randomUUID() + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.TEACHER)
                .build());

        HttpHeaders headers = authHeaders(teacher);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/notices", HttpMethod.POST, new HttpEntity<>(request, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void parentCanViewOwnChildFeesButNotOthers() {
        ResponseEntity<FeeDto[]> ownResponse = restTemplate.exchange(
                "/api/v1/fees/student/" + child.getId(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(parent)), FeeDto[].class);
        assertThat(ownResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ownResponse.getBody()).isNotEmpty();

        ResponseEntity<String> otherResponse = restTemplate.exchange(
                "/api/v1/fees/student/" + child.getId(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(otherParent)), String.class);
        assertThat(otherResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminUpdatingFeePaymentDerivesStatus() {
        FeeUpdateRequest request = new FeeUpdateRequest(new BigDecimal("1000.00"), null);

        HttpHeaders headers = adminHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<FeeDto> response = restTemplate.exchange(
                "/api/v1/fees/" + fee.getId(), HttpMethod.PATCH, new HttpEntity<>(request, headers), FeeDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(FeeStatus.PAID);
    }

    @Test
    void parentCannotUpdateFee() {
        FeeUpdateRequest request = new FeeUpdateRequest(new BigDecimal("500.00"), null);

        HttpHeaders headers = authHeaders(parent);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/fees/" + fee.getId(), HttpMethod.PATCH, new HttpEntity<>(request, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
