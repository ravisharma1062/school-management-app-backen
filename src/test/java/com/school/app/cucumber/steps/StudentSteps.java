package com.school.app.cucumber.steps;

import com.school.app.cucumber.support.World;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.HashMap;
import java.util.Map;

public class StudentSteps {

    @Autowired
    private World world;

    @When("a student is created with name {string} roll {string} class {string} section {string} dob {string}")
    public void aStudentIsCreated(String name, String rollNo, String studentClass, String section, String dob) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("rollNo", rollNo);
        body.put("studentClass", studentClass);
        body.put("section", section);
        body.put("dob", dob);
        world.exchange(HttpMethod.POST, "/api/v1/students", body);
    }

    @When("a student is created with a blank name and roll {string} class {string} section {string} dob {string}")
    public void aStudentIsCreatedWithBlankName(String rollNo, String studentClass, String section, String dob) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "");
        body.put("rollNo", rollNo);
        body.put("studentClass", studentClass);
        body.put("section", section);
        body.put("dob", dob);
        world.exchange(HttpMethod.POST, "/api/v1/students", body);
    }

    @When("student {string} is fetched by id")
    public void studentIsFetchedById(String alias) {
        world.exchange(HttpMethod.GET, "/api/v1/students/" + world.student(alias).getId(), null);
    }

    @When("the student list is requested")
    public void theStudentListIsRequested() {
        world.exchange(HttpMethod.GET, "/api/v1/students", null);
    }

    @When("student {string} is renamed to {string}")
    public void studentIsRenamedTo(String alias, String newName) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", newName);
        world.exchange(HttpMethod.PATCH, "/api/v1/students/" + world.student(alias).getId(), body);
    }
}
