package com.school.app.attendance;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import com.school.app.student.Student;
import com.school.app.student.StudentRepository;
import com.school.app.timetable.TimetableCreateRequest;
import com.school.app.timetable.TimetableDto;
import com.school.app.user.Role;
import com.school.app.user.Teacher;
import com.school.app.user.TeacherRepository;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceTimetableIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "Password@123";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User teacherUser;
    private Teacher teacher;
    private User parent;
    private User otherParent;
    private Student child;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        teacherUser = userRepository.save(User.builder()
                .name("Teacher " + suffix)
                .email("teacher-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.TEACHER)
                .build());
        teacher = teacherRepository.save(Teacher.builder().user(teacherUser).build());

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
                .studentClass("7")
                .section("C")
                .dob(LocalDate.of(2013, 3, 3))
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

    private HttpHeaders adminHeaders() {
        LoginRequest request = new LoginRequest("admin@school.app", "Admin@123");
        AuthResponse response = restTemplate.postForObject("/api/v1/auth/login", request, AuthResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(response.accessToken());
        return headers;
    }

    @Test
    void teacherCanMarkAttendance() {
        AttendanceMarkRequest request = new AttendanceMarkRequest(child.getId(), LocalDate.now(), AttendanceStatus.PRESENT);
        HttpHeaders headers = authHeaders(teacherUser);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<AttendanceDto> response = restTemplate.exchange(
                "/api/v1/attendance", HttpMethod.POST, new HttpEntity<>(request, headers), AttendanceDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    void parentCannotMarkAttendance() {
        AttendanceMarkRequest request = new AttendanceMarkRequest(child.getId(), LocalDate.now(), AttendanceStatus.PRESENT);
        HttpHeaders headers = authHeaders(parent);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/attendance", HttpMethod.POST, new HttpEntity<>(request, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void parentCanViewOwnChildAttendanceButNotOthers() {
        HttpHeaders teacherHeaders = authHeaders(teacherUser);
        teacherHeaders.setContentType(MediaType.APPLICATION_JSON);
        AttendanceMarkRequest markRequest = new AttendanceMarkRequest(child.getId(), LocalDate.now(), AttendanceStatus.PRESENT);
        restTemplate.exchange("/api/v1/attendance", HttpMethod.POST, new HttpEntity<>(markRequest, teacherHeaders), AttendanceDto.class);

        ResponseEntity<AttendanceDto[]> ownResponse = restTemplate.exchange(
                "/api/v1/attendance/student/" + child.getId(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(parent)), AttendanceDto[].class);
        assertThat(ownResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ownResponse.getBody()).isNotEmpty();

        ResponseEntity<String> otherResponse = restTemplate.exchange(
                "/api/v1/attendance/student/" + child.getId(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(otherParent)), String.class);
        assertThat(otherResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanCreateTimetableEntryButTeacherCannot() {
        TimetableCreateRequest request = new TimetableCreateRequest(
                "7", "C", DayOfWeek.MONDAY, 1, "Math", teacher.getId());

        HttpHeaders adminHeaders = adminHeaders();
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<TimetableDto> adminResponse = restTemplate.exchange(
                "/api/v1/timetable", HttpMethod.POST, new HttpEntity<>(request, adminHeaders), TimetableDto.class);
        assertThat(adminResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        HttpHeaders teacherHeaders = authHeaders(teacherUser);
        teacherHeaders.setContentType(MediaType.APPLICATION_JSON);
        TimetableCreateRequest secondSlot = new TimetableCreateRequest(
                "7", "C", DayOfWeek.TUESDAY, 1, "Science", teacher.getId());
        ResponseEntity<String> teacherResponse = restTemplate.exchange(
                "/api/v1/timetable", HttpMethod.POST, new HttpEntity<>(secondSlot, teacherHeaders), String.class);
        assertThat(teacherResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void allRolesCanViewTimetable() {
        ResponseEntity<List> response = restTemplate.exchange(
                "/api/v1/timetable/7/C", HttpMethod.GET,
                new HttpEntity<>(authHeaders(parent)), List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
