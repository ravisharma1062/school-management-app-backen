package com.school.app.analytics;

import com.school.app.attendance.Attendance;
import com.school.app.attendance.AttendanceRepository;
import com.school.app.attendance.AttendanceStatus;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "Password@123";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private AttendanceRepository attendanceRepository;
    @Autowired
    private FeeRepository feeRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User teacher;
    private String studentClass;
    private Student atRiskStudent;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        studentClass = suffix.substring(0, 6);

        teacher = userRepository.save(User.builder()
                .name("Analytics Teacher " + suffix)
                .email("analytics-teacher-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.TEACHER)
                .build());

        atRiskStudent = studentRepository.save(Student.builder()
                .name("At Risk Student " + suffix)
                .rollNo("AR-" + suffix)
                .studentClass(studentClass)
                .section("A")
                .dob(LocalDate.of(2012, 1, 1))
                .build());

        // 2 present out of 10 marked days -> well below the 75% attendance threshold.
        LocalDate day = LocalDate.now();
        for (int i = 0; i < 10; i++) {
            attendanceRepository.save(Attendance.builder()
                    .student(atRiskStudent)
                    .date(day.minusDays(i))
                    .status(i < 2 ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT)
                    .markedBy(teacher)
                    .build());
        }

        feeRepository.save(Fee.builder()
                .student(atRiskStudent)
                .term("Term 1")
                .amountDue(new BigDecimal("1000.00"))
                .amountPaid(BigDecimal.ZERO)
                .status(FeeStatus.OVERDUE)
                .dueDate(LocalDate.now().minusDays(30))
                .build());
    }

    @Test
    void nonAdminCannotAccessAnalytics() {
        HttpHeaders headers = authHeaders(teacher);

        assertThat(get("/api/v1/analytics/attendance", headers).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/analytics/fees/summary", headers).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/analytics/at-risk-students", headers).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void feeSummaryReflectsOverdueFeeForTheClass() {
        ResponseEntity<FeeSummaryDto> response = restTemplate.exchange(
                "/api/v1/analytics/fees/summary?class=" + studentClass, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), FeeSummaryDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        FeeSummaryDto body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.totalDue()).isEqualByComparingTo("1000.00");
        assertThat(body.totalPaid()).isEqualByComparingTo("0.00");
        assertThat(body.overdueCount()).isEqualTo(1);
        assertThat(body.collectionPercentage()).isEqualTo(0.0);
    }

    @Test
    void atRiskStudentsIncludesLowAttendanceAndOverdueFeeStudent() {
        ResponseEntity<AtRiskStudentDto[]> response = restTemplate.exchange(
                "/api/v1/analytics/at-risk-students", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), AtRiskStudentDto[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AtRiskStudentDto match = java.util.Arrays.stream(response.getBody())
                .filter(dto -> dto.studentId().equals(atRiskStudent.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected student not found in at-risk list"));

        assertThat(match.attendanceAtRisk()).isTrue();
        assertThat(match.feeAtRisk()).isTrue();
        assertThat(match.attendancePercentage()).isEqualTo(20.0);
        assertThat(match.maxDaysOverdue()).isGreaterThanOrEqualTo(30);
    }

    @Test
    void attendanceTrendCoversTheRequestedRange() {
        ResponseEntity<AttendanceTrendPointDto[]> response = restTemplate.exchange(
                "/api/v1/analytics/attendance?class=" + studentClass + "&range=10", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), AttendanceTrendPointDto[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(10);
        long totalPresent = java.util.Arrays.stream(response.getBody()).mapToLong(AttendanceTrendPointDto::presentCount).sum();
        assertThat(totalPresent).isEqualTo(2);
    }

    private ResponseEntity<String> get(String path, HttpHeaders headers) {
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
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
