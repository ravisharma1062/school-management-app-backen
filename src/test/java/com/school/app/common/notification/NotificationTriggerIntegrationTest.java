package com.school.app.common.notification;

import com.school.app.attendance.AttendanceMarkRequest;
import com.school.app.attendance.AttendanceStatus;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the ATTENDANCE_ABSENT trigger both fires and does not throw
 * LazyInitializationException when reaching across the Student -> parent (User) lazy
 * association after the fetching transaction has already closed (see AttendanceService.mark()).
 */
class NotificationTriggerIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "Password@123";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private NotificationLogRepository notificationLogRepository;
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
                .email("teacher-notif-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.TEACHER)
                .build());

        parent = userRepository.save(User.builder()
                .name("Parent " + suffix)
                .email("parent-notif-" + suffix + "@school.app")
                .phone("+911234567890")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.PARENT)
                .build());

        child = studentRepository.save(Student.builder()
                .name("Child " + suffix)
                .rollNo("N-" + suffix)
                .studentClass("4")
                .section("A")
                .dob(LocalDate.of(2016, 1, 1))
                .parent(parent)
                .build());
    }

    @Test
    void markingAbsentDoesNotThrowAndWritesASkippedSmsLog() {
        AttendanceMarkRequest request = new AttendanceMarkRequest(child.getId(), LocalDate.now(), AttendanceStatus.ABSENT);

        HttpHeaders headers = authHeaders(teacher);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/attendance", HttpMethod.POST, new HttpEntity<>(request, headers), String.class);

        // The critical assertion: this must not be 500. A LazyInitializationException reaching
        // the parent User off the Student association would surface here as a 500.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<NotificationLog> logs = notificationLogRepository.findAll().stream()
                .filter(l -> l.getEventType() == NotificationEventType.ATTENDANCE_ABSENT)
                .filter(l -> l.getRecipient().equals(parent.getPhone()))
                .toList();

        assertThat(logs).isNotEmpty();
        // Msg91 has no credentials in the test environment, so this must be SKIPPED, not FAILED.
        assertThat(logs.get(logs.size() - 1).getStatus()).isEqualTo(NotificationStatus.SKIPPED);
    }

    private HttpHeaders authHeaders(User user) {
        LoginRequest request = new LoginRequest(user.getEmail(), TEST_PASSWORD);
        AuthResponse response = restTemplate.postForObject("/api/v1/auth/login", request, AuthResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(response.accessToken());
        return headers;
    }
}
