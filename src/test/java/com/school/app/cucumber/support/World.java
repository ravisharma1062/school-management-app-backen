package com.school.app.cucumber.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.app.fee.Fee;
import com.school.app.student.Student;
import com.school.app.user.Teacher;
import com.school.app.user.User;
import io.cucumber.spring.ScenarioScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-scenario shared state (the Cucumber "World"). A fresh instance is created for every scenario
 * so seeded users/students and the last HTTP response never leak between scenarios.
 */
@Component
@ScenarioScope
public class World {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    /** Seeded domain objects, keyed by the alias used in the feature file (e.g. "parent1"). */
    private final Map<String, User> users = new HashMap<>();
    private final Map<String, Student> students = new HashMap<>();
    private final Map<String, Fee> fees = new HashMap<>();
    /** Teacher-profile rows (the {@code teachers} table), keyed by the owning user's alias. */
    private final Map<String, Teacher> teacherProfiles = new HashMap<>();
    /** Access tokens obtained via login, keyed by user alias. */
    private final Map<String, String> tokens = new HashMap<>();
    /** Scratch values captured during a scenario (e.g. a refresh token). */
    private final Map<String, String> vars = new HashMap<>();

    private String currentToken;
    private ResponseEntity<String> lastResponse;

    // ---- entity registry -------------------------------------------------

    public void putUser(String key, User user) {
        users.put(key, user);
    }

    public User user(String key) {
        User user = users.get(key);
        if (user == null) {
            throw new IllegalArgumentException("No seeded user with alias '" + key + "'");
        }
        return user;
    }

    public void putStudent(String key, Student student) {
        students.put(key, student);
    }

    public Student student(String key) {
        Student student = students.get(key);
        if (student == null) {
            throw new IllegalArgumentException("No seeded student with alias '" + key + "'");
        }
        return student;
    }

    public void putFee(String key, Fee fee) {
        fees.put(key, fee);
    }

    public Fee fee(String key) {
        Fee fee = fees.get(key);
        if (fee == null) {
            throw new IllegalArgumentException("No seeded fee with alias '" + key + "'");
        }
        return fee;
    }

    public void putTeacherProfile(String userKey, Teacher teacher) {
        teacherProfiles.put(userKey, teacher);
    }

    public Teacher teacherProfile(String userKey) {
        Teacher teacher = teacherProfiles.get(userKey);
        if (teacher == null) {
            throw new IllegalArgumentException("No teacher profile for user alias '" + userKey + "'");
        }
        return teacher;
    }

    public void putVar(String key, String value) {
        vars.put(key, value);
    }

    public String var(String key) {
        return vars.get(key);
    }

    // ---- authentication --------------------------------------------------

    public void rememberToken(String userKey, String token) {
        tokens.put(userKey, token);
    }

    public void authenticateAs(String token) {
        this.currentToken = token;
    }

    public void authenticateAsUser(String userKey) {
        String token = tokens.get(userKey);
        if (token == null) {
            throw new IllegalArgumentException("User '" + userKey + "' has not logged in yet");
        }
        this.currentToken = token;
    }

    public void clearAuthentication() {
        this.currentToken = null;
    }

    // ---- HTTP ------------------------------------------------------------

    public ResponseEntity<String> exchange(HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (currentToken != null) {
            headers.setBearerAuth(currentToken);
        }
        HttpEntity<?> entity = new HttpEntity<>(body, headers);
        lastResponse = restTemplate.exchange(path, method, entity, String.class);
        return lastResponse;
    }

    public ResponseEntity<String> lastResponse() {
        if (lastResponse == null) {
            throw new IllegalStateException("No HTTP request has been made yet");
        }
        return lastResponse;
    }

    // ---- JSON helpers ----------------------------------------------------

    public JsonNode body() {
        try {
            return objectMapper.readTree(lastResponse().getBody());
        } catch (Exception e) {
            throw new RuntimeException("Response body is not valid JSON: " + lastResponse().getBody(), e);
        }
    }

    /** Reads a field via a JSON pointer, e.g. "/status" or "/content/0/name". */
    public JsonNode at(String pointer) {
        return body().at(pointer);
    }
}
