package com.school.app.timetable;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import com.school.app.user.Role;
import com.school.app.user.Teacher;
import com.school.app.user.TeacherRepository;
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

import java.time.DayOfWeek;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TimetableArchiveIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "Password@123";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private TimetableRepository timetableRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User teacherUser;
    private Timetable entry;
    private String studentClass;
    private String section;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        // (class, section, day_of_week, period) is unique in the DB, and the Testcontainers
        // Postgres instance is shared across the whole test run — the section must be unique
        // per test invocation, not just per test class, or repeated setUp() calls collide.
        studentClass = "9";
        section = suffix.substring(0, 6);

        teacherUser = userRepository.save(User.builder()
                .name("Teacher " + suffix)
                .email("teacher-tt-arch-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.TEACHER)
                .build());

        Teacher teacher = teacherRepository.save(Teacher.builder()
                .user(teacherUser)
                .subjects("Maths")
                .classesAssigned(studentClass + "-" + section)
                .build());

        entry = timetableRepository.save(Timetable.builder()
                .studentClass(studentClass)
                .section(section)
                .dayOfWeek(DayOfWeek.MONDAY)
                .period(1)
                .subject("Maths")
                .teacher(teacher)
                .build());
    }

    @Test
    void adminCanArchiveAndRestoreTimetableEntry() {
        HttpHeaders headers = adminHeaders();

        ResponseEntity<TimetableDto> archiveResponse = restTemplate.exchange(
                "/api/v1/timetable/" + entry.getId() + "/archive", HttpMethod.PATCH,
                new HttpEntity<>(headers), TimetableDto.class);
        assertThat(archiveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(archiveResponse.getBody()).isNotNull();
        assertThat(archiveResponse.getBody().active()).isFalse();

        ResponseEntity<TimetableDto> restoreResponse = restTemplate.exchange(
                "/api/v1/timetable/" + entry.getId() + "/restore", HttpMethod.PATCH,
                new HttpEntity<>(headers), TimetableDto.class);
        assertThat(restoreResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restoreResponse.getBody()).isNotNull();
        assertThat(restoreResponse.getBody().active()).isTrue();
    }

    @Test
    void teacherCannotArchiveTimetableEntry() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/timetable/" + entry.getId() + "/archive", HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(teacherUser)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void archivedEntryExcludedFromDefaultViewButIncludedWithIncludeArchived() {
        HttpHeaders headers = adminHeaders();
        restTemplate.exchange(
                "/api/v1/timetable/" + entry.getId() + "/archive", HttpMethod.PATCH,
                new HttpEntity<>(headers), TimetableDto.class);

        ResponseEntity<String> defaultView = restTemplate.exchange(
                "/api/v1/timetable/" + studentClass + "/" + section, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(defaultView.getBody()).isNotNull();
        assertThat(defaultView.getBody()).doesNotContain(entry.getId().toString());

        ResponseEntity<String> fullView = restTemplate.exchange(
                "/api/v1/timetable/" + studentClass + "/" + section + "?includeArchived=true", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(fullView.getBody()).isNotNull();
        assertThat(fullView.getBody()).contains(entry.getId().toString());
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
