package com.school.app.common.security;

import com.school.app.common.AbstractIntegrationTest;
import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import com.school.app.school.SchoolStatus;
import com.school.app.student.Student;
import com.school.app.student.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the V17 migration's row-level-security policies are real, not just present in the
 * schema — i.e. that they actually block access, independent of Hibernate's {@code @TenantId}
 * filter (which the {@link CrossTenantIsolationIntegrationTest} diagnostic already proved has its
 * own gap for primary-key loads).
 *
 * <p>Testcontainers' default Postgres role (like most local/dev roles) is effectively a
 * superuser, and Postgres exempts superusers and — without {@code FORCE ROW LEVEL SECURITY} —
 * even the owning role from RLS. So this test provisions a second, genuinely restricted, non-
 * superuser role and connects as that role directly via JDBC (bypassing the app's own
 * connection pool and Hibernate entirely) to give RLS a fair, meaningful test.
 */
class RowLevelSecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SchoolRepository schoolRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Test
    void rlsBlocksATenantUnawareRawQueryEvenWithoutHibernate() throws Exception {
        School otherSchool = schoolRepository.save(School.builder()
                .name("RLS Test School")
                .slug("rls-test-" + UUID.randomUUID())
                .status(SchoolStatus.ACTIVE)
                .build());
        TenantContext.set(otherSchool.getId());
        Student otherSchoolStudent = studentRepository.save(Student.builder()
                .name("RLS Probe Student")
                .rollNo("RLS-" + UUID.randomUUID().toString().substring(0, 8))
                .studentClass("RLS")
                .section("X")
                .dob(LocalDate.of(2013, 1, 1))
                .build());
        TenantContext.clear();

        String restrictedUser = "rls_test_role_" + System.identityHashCode(this);
        String restrictedPassword = "rls_test_password";
        jdbcTemplate.execute("DROP ROLE IF EXISTS " + restrictedUser);
        jdbcTemplate.execute("CREATE ROLE " + restrictedUser + " LOGIN PASSWORD '" + restrictedPassword + "' NOSUPERUSER");
        jdbcTemplate.execute("GRANT SELECT ON students TO " + restrictedUser);

        try (Connection restrictedConnection = DriverManager.getConnection(jdbcUrl, restrictedUser, restrictedPassword)) {
            assertThat(restrictedConnection.getMetaData().getUserName()).isEqualTo(restrictedUser);

            // No app.current_school_id set at all: the policy's current_setting(..., true) call
            // returns NULL, which never equals any school_id — fail-safe default is zero rows.
            try (PreparedStatement ps = restrictedConnection.prepareStatement(
                    "SELECT id FROM students WHERE id = ?")) {
                ps.setObject(1, otherSchoolStudent.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("RLS must hide the row when no tenant GUC is set").isFalse();
                }
            }

            // Positive control: with the GUC set to a DIFFERENT school, still zero rows — proves
            // the policy is actually comparing values, not just erroring into an empty result.
            try (PreparedStatement setWrongTenant = restrictedConnection.prepareStatement(
                    "SELECT set_config('app.current_school_id', ?, false)")) {
                setWrongTenant.setString(1, UUID.randomUUID().toString());
                setWrongTenant.execute();
            }
            try (PreparedStatement ps = restrictedConnection.prepareStatement(
                    "SELECT id FROM students WHERE id = ?")) {
                ps.setObject(1, otherSchoolStudent.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("RLS must hide the row for the WRONG tenant too").isFalse();
                }
            }

            // Positive control: with the GUC set to the CORRECT school, the row appears — proves
            // the policy isn't simply blocking everything unconditionally.
            try (PreparedStatement setCorrectTenant = restrictedConnection.prepareStatement(
                    "SELECT set_config('app.current_school_id', ?, false)")) {
                setCorrectTenant.setString(1, otherSchool.getId().toString());
                setCorrectTenant.execute();
            }
            try (PreparedStatement ps = restrictedConnection.prepareStatement(
                    "SELECT id FROM students WHERE id = ?")) {
                ps.setObject(1, otherSchoolStudent.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("RLS must allow the row for its OWN tenant").isTrue();
                }
            }
        } finally {
            jdbcTemplate.execute("REVOKE ALL PRIVILEGES ON students FROM " + restrictedUser);
            jdbcTemplate.execute("DROP ROLE IF EXISTS " + restrictedUser);
        }
    }
}
