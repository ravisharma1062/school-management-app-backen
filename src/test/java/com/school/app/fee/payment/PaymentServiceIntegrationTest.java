package com.school.app.fee.payment;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import com.school.app.fee.Fee;
import com.school.app.fee.FeeRepository;
import com.school.app.fee.FeeStatus;
import com.school.app.student.Student;
import com.school.app.student.StudentRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentServiceIntegrationTest extends AbstractIntegrationTest {

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
    private Fee outstandingFee;
    private Fee paidFee;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        parent = userRepository.save(User.builder()
                .name("Parent " + suffix)
                .email("parent-pay-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.PARENT)
                .build());

        otherParent = userRepository.save(User.builder()
                .name("Other Parent " + suffix)
                .email("other-parent-pay-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.PARENT)
                .build());

        Student child = studentRepository.save(Student.builder()
                .name("Child " + suffix)
                .rollNo("PAY-" + suffix)
                .studentClass("6")
                .section("A")
                .dob(LocalDate.of(2014, 1, 1))
                .parent(parent)
                .build());

        outstandingFee = feeRepository.save(Fee.builder()
                .student(child)
                .term("Term 1")
                .amountDue(new BigDecimal("1000.00"))
                .amountPaid(BigDecimal.ZERO)
                .status(FeeStatus.PENDING)
                .dueDate(LocalDate.now().plusDays(30))
                .build());

        paidFee = feeRepository.save(Fee.builder()
                .student(child)
                .term("Term 2")
                .amountDue(new BigDecimal("1000.00"))
                .amountPaid(new BigDecimal("1000.00"))
                .status(FeeStatus.PAID)
                .dueDate(LocalDate.now().plusDays(30))
                .build());
    }

    @Test
    void parentInitiatingPaymentGetsServiceUnavailableSinceRazorpayIsNotConfiguredInTests() {
        // This is the expected, correct behavior in an environment without RAZORPAY_KEY_ID/SECRET —
        // proves authorization passed and the request reached the gateway call, which then
        // reports its own not-configured state rather than the app crashing or silently no-op'ing.
        ResponseEntity<String> response = post(outstandingFee.getId(), authHeaders(parent));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void parentCannotInitiatePaymentForAnotherParentsChild() {
        ResponseEntity<String> response = post(outstandingFee.getId(), authHeaders(otherParent));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void initiatingPaymentWithNothingOutstandingIsRejected() {
        ResponseEntity<String> response = post(paidFee.getId(), authHeaders(parent));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void webhookWithInvalidSignatureIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Razorpay-Signature", "not-a-real-signature");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/payments/webhook", HttpMethod.POST,
                new HttpEntity<>("{\"event\":\"payment.captured\"}", headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<String> post(UUID feeId, HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                "/api/v1/payments/initiate", HttpMethod.POST,
                new HttpEntity<>(new PaymentInitiateRequest(feeId), headers), String.class);
    }

    private HttpHeaders authHeaders(User user) {
        LoginRequest request = new LoginRequest(user.getEmail(), TEST_PASSWORD);
        AuthResponse response = restTemplate.postForObject("/api/v1/auth/login", request, AuthResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(response.accessToken());
        return headers;
    }
}
