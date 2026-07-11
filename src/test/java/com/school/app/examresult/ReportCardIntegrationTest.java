package com.school.app.examresult;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
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

class ReportCardIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "Password@123";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private ExamResultRepository examResultRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User parent;
    private User otherParent;
    private Student child;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        parent = userRepository.save(User.builder()
                .name("Parent " + suffix)
                .email("parent-rc-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.PARENT)
                .build());

        otherParent = userRepository.save(User.builder()
                .name("Other Parent " + suffix)
                .email("other-parent-rc-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.PARENT)
                .build());

        child = studentRepository.save(Student.builder()
                .name("Report Card Child " + suffix)
                .rollNo("RC-" + suffix)
                .studentClass("6")
                .section("A")
                .dob(LocalDate.of(2015, 1, 1))
                .parent(parent)
                .build());

        examResultRepository.save(ExamResult.builder()
                .student(child)
                .subject("Maths")
                .examName("Midterm")
                .marksObtained(new BigDecimal("85.00"))
                .maxMarks(new BigDecimal("100.00"))
                .grade("A")
                .term("Term 1")
                .build());
    }

    @Test
    void parentCanDownloadOwnChildsReportCard() {
        ResponseEntity<byte[]> response = restTemplate.exchange(
                "/api/v1/exam-results/student/" + child.getId() + "/report-card", HttpMethod.GET,
                new HttpEntity<>(authHeaders(parent)), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getBody()).isNotNull();
        assertThat(new String(response.getBody(), 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    }

    @Test
    void parentCannotDownloadAnotherParentsChildReportCard() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/exam-results/student/" + child.getId() + "/report-card", HttpMethod.GET,
                new HttpEntity<>(authHeaders(otherParent)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private HttpHeaders authHeaders(User user) {
        LoginRequest request = new LoginRequest(user.getEmail(), TEST_PASSWORD);
        AuthResponse response = restTemplate.postForObject("/api/v1/auth/login", request, AuthResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(response.accessToken());
        return headers;
    }
}
