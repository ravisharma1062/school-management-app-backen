package com.school.app.homework.submission;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import com.school.app.homework.Homework;
import com.school.app.homework.HomeworkRepository;
import com.school.app.student.Student;
import com.school.app.student.StudentRepository;
import com.school.app.user.Role;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HomeworkSubmissionIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "Password@123";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private HomeworkRepository homeworkRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User teacher;
    private User parent;
    private User otherParent;
    private Student child;
    private Homework homework;
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);

        teacher = userRepository.save(User.builder()
                .name("Teacher " + suffix)
                .email("teacher-hw-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.TEACHER)
                .build());

        parent = userRepository.save(User.builder()
                .name("Parent " + suffix)
                .email("parent-hw-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.PARENT)
                .build());

        otherParent = userRepository.save(User.builder()
                .name("Other Parent " + suffix)
                .email("other-parent-hw-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.PARENT)
                .build());

        child = studentRepository.save(Student.builder()
                .name("Child " + suffix)
                .rollNo("HW-" + suffix)
                .studentClass("7")
                .section("A")
                .dob(LocalDate.of(2013, 1, 1))
                .parent(parent)
                .build());

        homework = homeworkRepository.save(Homework.builder()
                .studentClass("7")
                .section("A")
                .subject("Math")
                .title("Worksheet 1")
                .dueDate(LocalDate.now().plusDays(7))
                .createdBy(teacher)
                .build());
    }

    @Test
    void parentSubmitsHomeworkForTheirOwnChild() {
        ResponseEntity<HomeworkSubmissionDto> response = submit(homework.getId(), child.getId(), authHeaders(parent));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(HomeworkSubmissionStatus.SUBMITTED);
        assertThat(response.getBody().fileName()).isEqualTo("answer.txt");
    }

    @Test
    void parentCannotSubmitForAnotherParentsChild() {
        ResponseEntity<String> response = submit(homework.getId(), child.getId(), authHeaders(otherParent), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void secondSubmissionForTheSameHomeworkIsRejected() {
        submit(homework.getId(), child.getId(), authHeaders(parent));

        ResponseEntity<String> response = submit(homework.getId(), child.getId(), authHeaders(parent), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void teacherGradesASubmissionAndParentSeesTheGrade() {
        HomeworkSubmissionDto submitted = submit(homework.getId(), child.getId(), authHeaders(parent)).getBody();

        HttpHeaders teacherHeaders = authHeaders(teacher);
        teacherHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<HomeworkSubmissionDto> gradeResponse = restTemplate.exchange(
                "/api/v1/homework/submissions/" + submitted.id(), HttpMethod.PATCH,
                new HttpEntity<>(new HomeworkSubmissionGradeRequest("Well done", "A"), teacherHeaders),
                HomeworkSubmissionDto.class);

        assertThat(gradeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(gradeResponse.getBody()).isNotNull();
        assertThat(gradeResponse.getBody().status()).isEqualTo(HomeworkSubmissionStatus.GRADED);
        assertThat(gradeResponse.getBody().grade()).isEqualTo("A");

        ResponseEntity<List<HomeworkSubmissionDto>> byStudent = restTemplate.exchange(
                "/api/v1/homework/submissions/student/" + child.getId(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(parent)),
                new ParameterizedTypeReference<>() {
                });
        assertThat(byStudent.getBody()).hasSize(1);
        assertThat(byStudent.getBody().get(0).grade()).isEqualTo("A");
    }

    @Test
    void teacherListsSubmissionsForAPieceOfHomework() {
        submit(homework.getId(), child.getId(), authHeaders(parent));

        ResponseEntity<List<HomeworkSubmissionDto>> response = restTemplate.exchange(
                "/api/v1/homework/" + homework.getId() + "/submissions", HttpMethod.GET,
                new HttpEntity<>(authHeaders(teacher)),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).studentId()).isEqualTo(child.getId());
    }

    @Test
    void submittedFileCanBeDownloadedByTheTeacherAndTheOwningParentButNotAnotherParent() {
        HomeworkSubmissionDto submitted = submit(homework.getId(), child.getId(), authHeaders(parent)).getBody();
        String url = "/api/v1/homework/submissions/" + submitted.id() + "/file";

        ResponseEntity<byte[]> teacherDownload = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(authHeaders(teacher)), byte[].class);
        assertThat(teacherDownload.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(teacherDownload.getBody(), StandardCharsets.UTF_8)).isEqualTo("42");

        ResponseEntity<byte[]> parentDownload = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(authHeaders(parent)), byte[].class);
        assertThat(parentDownload.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> forbidden = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(authHeaders(otherParent)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<HomeworkSubmissionDto> submit(UUID homeworkId, UUID studentId, HttpHeaders headers) {
        return submit(homeworkId, studentId, headers, HomeworkSubmissionDto.class);
    }

    private <T> ResponseEntity<T> submit(UUID homeworkId, UUID studentId, HttpHeaders headers, Class<T> responseType) {
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource fileResource = new ByteArrayResource("42".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "answer.txt";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);
        body.add("studentId", studentId.toString());

        return restTemplate.exchange(
                "/api/v1/homework/" + homeworkId + "/submissions", HttpMethod.POST,
                new HttpEntity<>(body, headers), responseType);
    }

    private HttpHeaders authHeaders(User user) {
        LoginRequest request = new LoginRequest(user.getEmail(), TEST_PASSWORD);
        AuthResponse response = restTemplate.postForObject("/api/v1/auth/login", request, AuthResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(response.accessToken());
        return headers;
    }
}
