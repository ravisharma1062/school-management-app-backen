package com.school.app.cucumber.steps;

import com.school.app.cucumber.support.World;
import com.school.app.fee.Fee;
import com.school.app.fee.FeeRepository;
import com.school.app.fee.FeeStatus;
import com.school.app.student.Student;
import com.school.app.student.StudentRepository;
import com.school.app.user.Role;
import com.school.app.user.Teacher;
import com.school.app.user.TeacherRepository;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seeding, authentication, and generic response assertions shared by every feature.
 */
public class CommonSteps {

    static final String TEST_PASSWORD = "Password@123";
    private static final String ADMIN_EMAIL = "admin@school.app";
    private static final String ADMIN_PASSWORD = "Admin@123";

    @Autowired
    private World world;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private FeeRepository feeRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    // ---- seeding ---------------------------------------------------------

    @Given("a user {string} with role {word}")
    public void aUserWithRole(String alias, String role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .name(alias + " " + suffix)
                .email(alias + "-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.valueOf(role))
                .build());
        world.putUser(alias, user);

        // A teacher also needs a `teachers` profile row so it can be assigned to timetable slots.
        if (user.getRole() == Role.TEACHER) {
            Teacher teacher = teacherRepository.save(Teacher.builder()
                    .user(user)
                    .subjects("General")
                    .classesAssigned("5A")
                    .build());
            world.putTeacherProfile(alias, teacher);
        }
    }

    @Given("a student {string} in class {string} section {string} with parent {string}")
    public void aStudentWithParent(String alias, String studentClass, String section, String parentAlias) {
        seedStudent(alias, studentClass, section, world.user(parentAlias));
    }

    @Given("a student {string} in class {string} section {string} with no parent")
    public void aStudentWithNoParent(String alias, String studentClass, String section) {
        seedStudent(alias, studentClass, section, null);
    }

    private void seedStudent(String alias, String studentClass, String section, User parent) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Student student = studentRepository.save(Student.builder()
                .name(alias + " " + suffix)
                .rollNo("R-" + suffix)
                .studentClass(studentClass)
                .section(section)
                .dob(LocalDate.of(2014, 1, 1))
                .parent(parent)
                .build());
        world.putStudent(alias, student);
    }

    @Given("a pending fee {string} of {double} for student {string}")
    public void aPendingFee(String alias, double amountDue, String studentAlias) {
        Fee fee = feeRepository.save(Fee.builder()
                .student(world.student(studentAlias))
                .term("Term 1")
                .amountDue(BigDecimal.valueOf(amountDue))
                .amountPaid(BigDecimal.ZERO)
                .status(FeeStatus.PENDING)
                .dueDate(LocalDate.now().plusDays(30))
                .build());
        world.putFee(alias, fee);
    }

    // ---- authentication --------------------------------------------------

    @Given("the admin is logged in")
    public void theAdminIsLoggedIn() {
        loginAndAuthenticate("admin", ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    @Given("{string} is logged in")
    @When("{string} logs in")
    public void isLoggedIn(String alias) {
        User user = world.user(alias);
        loginAndAuthenticate(alias, user.getEmail(), TEST_PASSWORD);
    }

    @Given("the client is not authenticated")
    public void theClientIsNotAuthenticated() {
        world.clearAuthentication();
    }

    private void loginAndAuthenticate(String alias, String email, String password) {
        world.clearAuthentication();
        world.exchange(org.springframework.http.HttpMethod.POST, "/api/v1/auth/login",
                Map.of("email", email, "password", password));
        assertThat(world.lastResponse().getStatusCode())
                .as("login for %s should succeed", alias)
                .isEqualTo(HttpStatus.OK);
        String token = world.at("/accessToken").asText();
        world.rememberToken(alias, token);
        world.authenticateAs(token);
        world.putVar("refreshToken", world.at("/refreshToken").asText());
    }

    // ---- generic assertions ---------------------------------------------

    @Then("the request should succeed")
    public void theRequestShouldSucceed() {
        assertThat(world.lastResponse().getStatusCode().is2xxSuccessful())
                .as("expected 2xx but got %s (body: %s)",
                        world.lastResponse().getStatusCode(), world.lastResponse().getBody())
                .isTrue();
    }

    @Then("the response status should be {int}")
    public void theResponseStatusShouldBe(int status) {
        assertThat(world.lastResponse().getStatusCode().value()).isEqualTo(status);
    }

    @Then("the request should be rejected as unauthorized")
    public void rejectedAsUnauthorized() {
        assertThat(world.lastResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Then("the request should be rejected as forbidden")
    public void rejectedAsForbidden() {
        assertThat(world.lastResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * A request with no (or invalid) credentials is rejected by the security chain's
     * {@code AuthenticationEntryPoint} with 401. (Authorization failures for an authenticated user
     * are 403 instead — see {@link #rejectedAsForbidden()}.)
     */
    @Then("the request should be rejected as unauthenticated")
    public void rejectedAsUnauthenticated() {
        assertThat(world.lastResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Then("the request should be rejected as a bad request")
    public void rejectedAsBadRequest() {
        assertThat(world.lastResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Then("the request should be rejected as not found")
    public void rejectedAsNotFound() {
        assertThat(world.lastResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Then("the JSON field {string} should equal {string}")
    public void theJsonFieldShouldEqual(String pointer, String expected) {
        assertThat(world.at(pointer).asText()).isEqualTo(expected);
    }

    @Then("the JSON field {string} should not be empty")
    public void theJsonFieldShouldNotBeEmpty(String pointer) {
        assertThat(world.at(pointer).asText()).isNotBlank();
    }

    @Then("the response should be a non-empty array")
    public void theResponseShouldBeANonEmptyArray() {
        assertThat(world.body().isArray()).as("expected a JSON array").isTrue();
        assertThat(world.body().size()).isGreaterThan(0);
    }

    @Then("the response should be an empty array")
    public void theResponseShouldBeAnEmptyArray() {
        assertThat(world.body().isArray()).as("expected a JSON array").isTrue();
        assertThat(world.body().size()).isZero();
    }

    @Then("the paged response should contain {int} item(s)")
    public void thePagedResponseShouldContain(int count) {
        assertThat(world.at("/totalElements").asInt()).isEqualTo(count);
    }
}
