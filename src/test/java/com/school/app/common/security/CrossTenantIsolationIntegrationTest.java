package com.school.app.common.security;

import com.school.app.attendance.Attendance;
import com.school.app.attendance.AttendanceRepository;
import com.school.app.attendance.AttendanceStatus;
import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import com.school.app.fee.Fee;
import com.school.app.fee.FeeRepository;
import com.school.app.fee.FeeStatus;
import com.school.app.notice.Notice;
import com.school.app.notice.NoticeRepository;
import com.school.app.notice.TargetRole;
import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import com.school.app.school.SchoolStatus;
import com.school.app.student.Student;
import com.school.app.student.StudentRepository;
import com.school.app.timetable.Timetable;
import com.school.app.timetable.TimetableRepository;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase MT-1's single most important test: two schools, each with a full set of records, and a
 * proof that School A's token cannot read, list, update, or archive ANY of School B's data across
 * a representative set of controllers — no matter the id used or the query issued.
 */
class CrossTenantIsolationIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "Password@123";

    @Autowired
    private SchoolRepository schoolRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private AttendanceRepository attendanceRepository;
    @Autowired
    private FeeRepository feeRepository;
    @Autowired
    private NoticeRepository noticeRepository;
    @Autowired
    private TimetableRepository timetableRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String tokenA;
    private String adminBEmail;
    private UUID studentAId;
    private UUID schoolBId;
    private Student studentB;
    private Fee feeB;
    private Notice noticeB;
    private String schoolBClass;
    private String schoolBSection;

    /**
     * Verification-only reads of School B's data after an isolation assertion, run with School
     * B's tenant temporarily active on this JUnit thread — same reasoning as
     * {@code seedFixturesForCurrentTenant}, just for reading afterward instead of seeding.
     */
    private <R> R asSchoolB(java.util.function.Supplier<R> query) {
        UUID previous = TenantContext.get();
        TenantContext.set(schoolBId);
        try {
            return query.get();
        } finally {
            TenantContext.set(previous);
        }
    }

    @BeforeEach
    void seedTwoSchools() {
        // School A is the default school AbstractIntegrationTest already scoped TenantContext to;
        // build a full fixture set in it directly (mirrors how every other *IntegrationTest seeds).
        // Every *IntegrationTest.@BeforeEach shares one Postgres container for the whole suite run
        // with no per-method cleanup, so — same as everywhere else in this suite — every email/slug
        // must be suffixed unique per invocation, not reused across test methods in this class.
        FixtureIds a = seedFixturesForCurrentTenant("A");
        studentAId = a.studentId();
        tokenA = login(a.adminEmail());

        // School B: a second, wholly separate tenant. Explicitly switch this JUnit thread's
        // TenantContext to it for the duration of seeding — direct repository saves outside HTTP
        // resolve @TenantId from this thread-local, same mechanism as AbstractIntegrationTest uses
        // for the default school.
        School schoolB = schoolRepository.save(School.builder()
                .name("School B")
                .slug("school-b-" + UUID.randomUUID())
                .status(SchoolStatus.ACTIVE)
                .build());
        schoolBId = schoolB.getId();
        TenantContext.set(schoolBId);
        FixtureIds b = seedFixturesForCurrentTenant("B");
        adminBEmail = b.adminEmail();
        schoolBClass = b.studentClass();
        schoolBSection = b.section();
        studentB = studentRepository.findById(b.studentId()).orElseThrow();
        feeB = feeRepository.findById(b.feeId()).orElseThrow();
        noticeB = noticeRepository.findById(b.noticeId()).orElseThrow();

        // Restore the JUnit thread to School A's context so nothing else in the base class or a
        // future test in this class accidentally seeds under School B.
        School schoolA = schoolRepository.findBySlug("default-school").orElseThrow();
        TenantContext.set(schoolA.getId());
    }

    private record FixtureIds(
            String adminEmail, UUID studentId, UUID feeId, UUID noticeId, String studentClass, String section) {
    }

    private FixtureIds seedFixturesForCurrentTenant(String label) {
        String suffix = label + "-" + UUID.randomUUID().toString().substring(0, 8);
        // School A is the same persistent default school across all 10 @Test methods in this
        // class (no per-method DB cleanup in this suite), so class/section must be unique per
        // invocation too, or the second test method's timetable insert collides with the first's.
        // section is VARCHAR(10), so its unique token has to stay short.
        String studentClass = "C" + suffix;
        String section = "S" + label + UUID.randomUUID().toString().substring(0, 6);

        User admin = userRepository.save(User.builder()
                .name("Admin " + label)
                .email("admin-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.ADMIN)
                .build());

        User teacherUser = userRepository.save(User.builder()
                .name("Teacher " + suffix)
                .email("teacher-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.TEACHER)
                .build());
        Teacher teacher = teacherRepository.save(Teacher.builder().user(teacherUser).build());

        User parent = userRepository.save(User.builder()
                .name("Parent " + suffix)
                .email("parent-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.PARENT)
                .build());

        Student student = studentRepository.save(Student.builder()
                .name("Child " + suffix)
                .rollNo("R-" + suffix)
                .studentClass(studentClass)
                .section(section)
                .dob(LocalDate.of(2013, 1, 1))
                .parent(parent)
                .build());

        attendanceRepository.save(Attendance.builder()
                .student(student)
                .date(LocalDate.now())
                .status(AttendanceStatus.PRESENT)
                .markedBy(teacherUser)
                .build());

        Fee fee = feeRepository.save(Fee.builder()
                .student(student)
                .term("Term 1 " + suffix)
                .amountDue(java.math.BigDecimal.valueOf(1000))
                .amountPaid(java.math.BigDecimal.ZERO)
                .status(FeeStatus.PENDING)
                .dueDate(LocalDate.now().plusDays(30))
                .build());

        Notice notice = noticeRepository.save(Notice.builder()
                .title("Secret notice for " + label)
                .description("Only " + label + " should ever see this")
                .targetRole(TargetRole.ALL)
                .createdBy(admin)
                .build());

        timetableRepository.save(Timetable.builder()
                .studentClass(studentClass)
                .section(section)
                .dayOfWeek(DayOfWeek.MONDAY)
                .period(1)
                .subject("Secret Subject " + label)
                .teacher(teacher)
                .build());

        return new FixtureIds(admin.getEmail(), student.getId(), fee.getId(), notice.getId(), studentClass, section);
    }

    private String login(String email) {
        LoginRequest request = new LoginRequest(email, TEST_PASSWORD);
        AuthResponse response = restTemplate.postForObject("/api/v1/auth/login", request, AuthResponse.class);
        return response.accessToken();
    }

    private HttpHeaders headersFor(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void diagnosticDirectRepositoryLookupRespectsTenantContext() {
        // Bypasses HTTP/JWT entirely: TenantContext on THIS thread is School A (restored at the
        // end of seedTwoSchools()). If Hibernate's @TenantId filter works at all, this must find
        // nothing for School B's student id.
        assertThat(TenantContext.get()).isNotEqualTo(TenantContext.NIL_SCHOOL_ID);
        assertThat(studentRepository.findById(studentB.getId())).isEmpty();
    }

    @Test
    void tokenACannotReadSchoolBsStudentById() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/students/" + studentB.getId(), HttpMethod.GET,
                new HttpEntity<>(headersFor(tokenA)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void tokenACannotUpdateSchoolBsStudent() {
        Map<String, Object> body = Map.of("name", "Hijacked Name");
        HttpHeaders headers = headersFor(tokenA);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/students/" + studentB.getId(), HttpMethod.PATCH,
                new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // And the record is provably untouched.
        Student stillIntact = asSchoolB(() -> studentRepository.findById(studentB.getId()).orElseThrow());
        assertThat(stillIntact.getName()).isNotEqualTo("Hijacked Name");
    }

    @Test
    void tokenACannotArchiveSchoolBsStudent() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/students/" + studentB.getId() + "/archive", HttpMethod.PATCH,
                new HttpEntity<>(headersFor(tokenA)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(asSchoolB(() -> studentRepository.findById(studentB.getId()).orElseThrow().isActive())).isTrue();
    }

    @Test
    void tokenACannotReadSchoolBsFees() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/fees/student/" + studentB.getId(), HttpMethod.GET,
                new HttpEntity<>(headersFor(tokenA)), String.class);

        // FeeService.getByStudent looks up the Student first (@TenantId-filtered, so it 404s
        // for a cross-tenant id) before ever reaching the Fee query below it.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).doesNotContain(feeB.getId().toString());
    }

    @Test
    void tokenACannotUpdateSchoolBsFee() {
        Map<String, Object> body = Map.of("amountPaid", 1000, "status", "PAID");
        HttpHeaders headers = headersFor(tokenA);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/fees/" + feeB.getId(), HttpMethod.PATCH,
                new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Fee stillIntact = asSchoolB(() -> feeRepository.findById(feeB.getId()).orElseThrow());
        assertThat(stillIntact.getStatus()).isEqualTo(FeeStatus.PENDING);
    }

    @Test
    void schoolBsTimetableSlotIsInvisibleToSchoolA() {
        // School A was never given a class/section matching School B's, so this must come back
        // empty for School A's token — not just "missing School B's subject", but no rows at all.
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/timetable/" + schoolBClass + "/" + schoolBSection, HttpMethod.GET,
                new HttpEntity<>(headersFor(tokenA)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContain("Secret Subject B");
        assertThat(response.getBody()).isEqualTo("[]");
    }

    @Test
    void schoolBsNoticeNeverAppearsInSchoolAsPaginatedList() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/notices?size=200", HttpMethod.GET,
                new HttpEntity<>(headersFor(tokenA)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContain("Secret notice for B");
        assertThat(response.getBody()).doesNotContain(noticeB.getId().toString());
    }

    @Test
    void tokenACannotArchiveSchoolBsNotice() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/notices/" + noticeB.getId() + "/archive", HttpMethod.PATCH,
                new HttpEntity<>(headersFor(tokenA)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(asSchoolB(() -> noticeRepository.findById(noticeB.getId()).orElseThrow().isActive())).isTrue();
    }

    @Test
    void schoolBsUsersNeverAppearInSchoolAsUserList() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users?size=200", HttpMethod.GET,
                new HttpEntity<>(headersFor(tokenA)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContain(adminBEmail);
    }

    @Test
    void provisionedSchoolsAreFullyIsolatedInBothDirections() {
        // Symmetry check: log in as School B's admin and confirm the reverse also holds — B
        // cannot see A's student either.
        String tokenB = login(adminBEmail);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/students/" + studentAId, HttpMethod.GET,
                new HttpEntity<>(headersFor(tokenB)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
