package com.school.app.messaging;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "Password@123";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private TimetableRepository timetableRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User parent;
    private User teachesChildTeacher;
    private User unrelatedTeacher;
    private String studentClass;
    private String section;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        studentClass = "8";
        section = suffix.substring(0, 6);

        parent = userRepository.save(User.builder()
                .name("Msg Parent " + suffix)
                .email("msg-parent-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.PARENT)
                .build());

        User teacherUser = userRepository.save(User.builder()
                .name("Msg Teacher " + suffix)
                .email("msg-teacher-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.TEACHER)
                .build());
        teachesChildTeacher = teacherUser;

        Teacher teacher = teacherRepository.save(Teacher.builder()
                .user(teacherUser)
                .subjects("Science")
                .build());

        timetableRepository.save(Timetable.builder()
                .studentClass(studentClass)
                .section(section)
                .dayOfWeek(DayOfWeek.MONDAY)
                .period(1)
                .subject("Science")
                .teacher(teacher)
                .build());

        unrelatedTeacher = userRepository.save(User.builder()
                .name("Unrelated Teacher " + suffix)
                .email("msg-unrelated-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.TEACHER)
                .build());
        teacherRepository.save(Teacher.builder().user(unrelatedTeacher).subjects("Art").build());

        studentRepository.save(Student.builder()
                .name("Msg Child " + suffix)
                .rollNo("MSG-" + suffix)
                .studentClass(studentClass)
                .section(section)
                .dob(LocalDate.of(2012, 1, 1))
                .parent(parent)
                .build());
    }

    @Test
    void parentCanStartAConversationWithTheirChildsTeacher() {
        ResponseEntity<ConversationDto> response = startConversation(authHeaders(parent), teachesChildTeacher.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().parentId()).isEqualTo(parent.getId());
        assertThat(response.getBody().teacherId()).isEqualTo(teachesChildTeacher.getId());
        assertThat(response.getBody().teacherName()).isEqualTo(teachesChildTeacher.getName());
    }

    @Test
    void startingTheSameConversationTwiceReturnsTheSameOne() {
        ConversationDto first = startConversation(authHeaders(parent), teachesChildTeacher.getId()).getBody();
        ConversationDto second = startConversation(authHeaders(parent), teachesChildTeacher.getId()).getBody();

        assertThat(second.id()).isEqualTo(first.id());
    }

    @Test
    void teacherCannotMessageAParentOfAStudentTheyDontTeach() {
        ResponseEntity<String> response = startConversation(authHeaders(unrelatedTeacher), parent.getId(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void participantsCanExchangeMessagesButOutsidersCannot() {
        ConversationDto conversation = startConversation(authHeaders(parent), teachesChildTeacher.getId()).getBody();

        HttpHeaders parentHeaders = authHeaders(parent);
        parentHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<MessageDto> sendResponse = restTemplate.exchange(
                "/api/v1/conversations/" + conversation.id() + "/messages", HttpMethod.POST,
                new HttpEntity<>(new MessageCreateRequest("Hello teacher"), parentHeaders), MessageDto.class);
        assertThat(sendResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sendResponse.getBody().body()).isEqualTo("Hello teacher");

        ResponseEntity<List<MessageDto>> teacherView = restTemplate.exchange(
                "/api/v1/conversations/" + conversation.id() + "/messages", HttpMethod.GET,
                new HttpEntity<>(authHeaders(teachesChildTeacher)),
                new ParameterizedTypeReference<>() {
                });
        assertThat(teacherView.getBody()).hasSize(1);
        assertThat(teacherView.getBody().get(0).senderId()).isEqualTo(parent.getId());

        ResponseEntity<String> outsiderView = restTemplate.exchange(
                "/api/v1/conversations/" + conversation.id() + "/messages", HttpMethod.GET,
                new HttpEntity<>(authHeaders(unrelatedTeacher)), String.class);
        assertThat(outsiderView.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void parentContactsListsOnlyTeachersOfTheirChildren() {
        ResponseEntity<List<ConversationContactDto>> response = restTemplate.exchange(
                "/api/v1/conversations/contacts", HttpMethod.GET,
                new HttpEntity<>(authHeaders(parent)),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(ConversationContactDto::id)
                .containsExactly(teachesChildTeacher.getId());
    }

    @Test
    void teacherContactsListsOnlyParentsOfTheirStudents() {
        ResponseEntity<List<ConversationContactDto>> response = restTemplate.exchange(
                "/api/v1/conversations/contacts", HttpMethod.GET,
                new HttpEntity<>(authHeaders(teachesChildTeacher)),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(ConversationContactDto::id)
                .containsExactly(parent.getId());
    }

    private ResponseEntity<ConversationDto> startConversation(HttpHeaders headers, UUID otherUserId) {
        return startConversation(headers, otherUserId, ConversationDto.class);
    }

    private <T> ResponseEntity<T> startConversation(HttpHeaders headers, UUID otherUserId, Class<T> responseType) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                "/api/v1/conversations", HttpMethod.POST,
                new HttpEntity<>(new ConversationCreateRequest(otherUserId), headers), responseType);
    }

    private HttpHeaders authHeaders(User user) {
        LoginRequest request = new LoginRequest(user.getEmail(), TEST_PASSWORD);
        AuthResponse response = restTemplate.postForObject("/api/v1/auth/login", request, AuthResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(response.accessToken());
        return headers;
    }
}
