package com.school.app.homework;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import com.school.app.examresult.ExamResultCreateRequest;
import com.school.app.examresult.ExamResultDto;
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

class HomeworkExamResultIntegrationTest extends AbstractIntegrationTest {

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

        child = studentRepository.save(Student.builder()
                .name("Child " + suffix)
                .rollNo("R-" + suffix)
                .studentClass("8")
                .section("A")
                .dob(LocalDate.of(2012, 2, 2))
                .parent(parent)
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
    void teacherCanPostHomeworkAndParentCanViewIt() {
        HomeworkCreateRequest request = new HomeworkCreateRequest(
                "8", "A", "Math", "Chapter 4 exercises", "Do all odd questions", LocalDate.now().plusDays(3));

        HttpHeaders teacherHeaders = authHeaders(teacher);
        teacherHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<HomeworkDto> createResponse = restTemplate.exchange(
                "/api/v1/homework", HttpMethod.POST, new HttpEntity<>(request, teacherHeaders), HomeworkDto.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> parentResponse = restTemplate.exchange(
                "/api/v1/homework/8/A", HttpMethod.GET, new HttpEntity<>(authHeaders(parent)), String.class);
        assertThat(parentResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void parentCannotPostHomework() {
        HomeworkCreateRequest request = new HomeworkCreateRequest(
                "8", "A", "Math", "Blocked", null, LocalDate.now().plusDays(1));

        HttpHeaders headers = authHeaders(parent);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/homework", HttpMethod.POST, new HttpEntity<>(request, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void teacherCanRecordExamResultWithServerComputedGrade() {
        ExamResultCreateRequest request = new ExamResultCreateRequest(
                child.getId(), "Science", "Midterm", new BigDecimal("88"), new BigDecimal("100"), "Term 1");

        HttpHeaders headers = authHeaders(teacher);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<ExamResultDto> response = restTemplate.exchange(
                "/api/v1/exam-results", HttpMethod.POST, new HttpEntity<>(request, headers), ExamResultDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().grade()).isEqualTo("A");
    }

    @Test
    void parentCanViewOwnChildExamResults() {
        ExamResultCreateRequest request = new ExamResultCreateRequest(
                child.getId(), "Science", "Midterm", new BigDecimal("88"), new BigDecimal("100"), "Term 1");
        HttpHeaders teacherHeaders = authHeaders(teacher);
        teacherHeaders.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.exchange("/api/v1/exam-results", HttpMethod.POST, new HttpEntity<>(request, teacherHeaders), ExamResultDto.class);

        ResponseEntity<ExamResultDto[]> response = restTemplate.exchange(
                "/api/v1/exam-results/student/" + child.getId(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(parent)), ExamResultDto[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }
}
