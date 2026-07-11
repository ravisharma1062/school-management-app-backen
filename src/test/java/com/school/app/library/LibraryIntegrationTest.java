package com.school.app.library;

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

class LibraryIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "Password@123";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User parent;
    private User otherParent;
    private Student child;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        parent = userRepository.save(User.builder()
                .name("Library Parent " + suffix)
                .email("library-parent-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.PARENT)
                .build());

        otherParent = userRepository.save(User.builder()
                .name("Other Parent " + suffix)
                .email("library-other-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.PARENT)
                .build());

        child = studentRepository.save(Student.builder()
                .name("Library Child " + suffix)
                .rollNo("LB-" + suffix)
                .studentClass("6")
                .section("A")
                .dob(LocalDate.of(2013, 1, 1))
                .parent(parent)
                .build());
    }

    @Test
    void nonAdminCannotAddABook() {
        ResponseEntity<String> response = createBook(authHeaders(parent), 1, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminAddsABookAndItIsSearchable() {
        BookDto book = createBook(adminHeaders(), 2, BookDto.class).getBody();
        assertThat(book).isNotNull();
        assertThat(book.availableCopies()).isEqualTo(2);

        ResponseEntity<String> search = restTemplate.exchange(
                "/api/v1/library/books?search=" + book.title().substring(0, 5), HttpMethod.GET,
                new HttpEntity<>(authHeaders(parent)), String.class);
        assertThat(search.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(search.getBody()).contains(book.id().toString());
    }

    @Test
    void issuingTheLastCopyThenIssuingAgainIsRejected() {
        BookDto book = createBook(adminHeaders(), 1, BookDto.class).getBody();

        ResponseEntity<BookIssueDto> first = restTemplate.exchange(
                "/api/v1/library/issues", HttpMethod.POST,
                new HttpEntity<>(new BookIssueCreateRequest(book.id(), child.getId()), adminHeaders()),
                BookIssueDto.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().status()).isEqualTo(BookIssueStatus.ISSUED);
        assertThat(first.getBody().bookTitle()).isEqualTo(book.title());
        assertThat(first.getBody().studentName()).isEqualTo(child.getName());

        Student secondChild = studentRepository.save(Student.builder()
                .name("Second Child")
                .rollNo("LB-second-" + UUID.randomUUID().toString().substring(0, 6))
                .studentClass("7")
                .section("B")
                .dob(LocalDate.of(2012, 1, 1))
                .parent(otherParent)
                .build());

        ResponseEntity<String> second = restTemplate.exchange(
                "/api/v1/library/issues", HttpMethod.POST,
                new HttpEntity<>(new BookIssueCreateRequest(book.id(), secondChild.getId()), adminHeaders()),
                String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void returningABookOnTimeIncursNoFineAndRestoresAvailability() {
        BookDto book = createBook(adminHeaders(), 1, BookDto.class).getBody();
        BookIssueDto issue = restTemplate.exchange(
                "/api/v1/library/issues", HttpMethod.POST,
                new HttpEntity<>(new BookIssueCreateRequest(book.id(), child.getId()), adminHeaders()),
                BookIssueDto.class).getBody();

        ResponseEntity<BookIssueDto> returned = restTemplate.exchange(
                "/api/v1/library/issues/" + issue.id() + "/return", HttpMethod.POST,
                new HttpEntity<>(adminHeaders()), BookIssueDto.class);

        assertThat(returned.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(returned.getBody().status()).isEqualTo(BookIssueStatus.RETURNED);
        assertThat(returned.getBody().fineAmount()).isEqualByComparingTo(java.math.BigDecimal.ZERO);

        ResponseEntity<String> search = restTemplate.exchange(
                "/api/v1/library/books?search=" + book.title().substring(0, 5), HttpMethod.GET,
                new HttpEntity<>(authHeaders(parent)), String.class);
        assertThat(search.getBody()).contains("\"availableCopies\":1");
    }

    @Test
    void returningABookTwiceIsRejected() {
        BookDto book = createBook(adminHeaders(), 1, BookDto.class).getBody();
        BookIssueDto issue = restTemplate.exchange(
                "/api/v1/library/issues", HttpMethod.POST,
                new HttpEntity<>(new BookIssueCreateRequest(book.id(), child.getId()), adminHeaders()),
                BookIssueDto.class).getBody();

        restTemplate.exchange(
                "/api/v1/library/issues/" + issue.id() + "/return", HttpMethod.POST,
                new HttpEntity<>(adminHeaders()), BookIssueDto.class);

        ResponseEntity<String> secondReturn = restTemplate.exchange(
                "/api/v1/library/issues/" + issue.id() + "/return", HttpMethod.POST,
                new HttpEntity<>(adminHeaders()), String.class);
        assertThat(secondReturn.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void parentSeesOwnChildsIssuesButNotAnotherParentsChild() {
        BookDto book = createBook(adminHeaders(), 1, BookDto.class).getBody();
        restTemplate.exchange(
                "/api/v1/library/issues", HttpMethod.POST,
                new HttpEntity<>(new BookIssueCreateRequest(book.id(), child.getId()), adminHeaders()),
                BookIssueDto.class);

        ResponseEntity<List> ownerView = restTemplate.exchange(
                "/api/v1/library/students/" + child.getId() + "/issues", HttpMethod.GET,
                new HttpEntity<>(authHeaders(parent)), List.class);
        assertThat(ownerView.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ownerView.getBody()).hasSize(1);

        ResponseEntity<String> outsiderView = restTemplate.exchange(
                "/api/v1/library/students/" + child.getId() + "/issues", HttpMethod.GET,
                new HttpEntity<>(authHeaders(otherParent)), String.class);
        assertThat(outsiderView.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private <T> ResponseEntity<T> createBook(HttpHeaders headers, int copies, Class<T> responseType) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        BookCreateRequest request = new BookCreateRequest(
                "Book " + UUID.randomUUID().toString().substring(0, 8), "Some Author", "ISBN-0000", copies);
        return restTemplate.exchange(
                "/api/v1/library/books", HttpMethod.POST, new HttpEntity<>(request, headers), responseType);
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
