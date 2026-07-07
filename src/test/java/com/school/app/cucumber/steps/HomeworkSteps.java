package com.school.app.cucumber.steps;

import com.school.app.cucumber.support.World;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.HashMap;
import java.util.Map;

public class HomeworkSteps {

    @Autowired
    private World world;

    @When("homework is posted for class {string} section {string} subject {string} titled {string} due {string}")
    public void homeworkIsPosted(String studentClass, String section, String subject, String title, String dueDate) {
        Map<String, Object> body = new HashMap<>();
        body.put("studentClass", studentClass);
        body.put("section", section);
        body.put("subject", subject);
        body.put("title", title);
        body.put("description", "Auto-generated description");
        body.put("dueDate", dueDate);
        world.exchange(HttpMethod.POST, "/api/v1/homework", body);
    }

    @When("homework for class {string} section {string} is requested")
    public void homeworkIsRequested(String studentClass, String section) {
        world.exchange(HttpMethod.GET, "/api/v1/homework/" + studentClass + "/" + section, null);
    }
}
