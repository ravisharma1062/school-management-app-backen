package com.school.app.cucumber.steps;

import com.school.app.cucumber.support.World;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.HashMap;
import java.util.Map;

public class TimetableSteps {

    @Autowired
    private World world;

    @When("a timetable entry is created for class {string} section {string} on {string} period {int} subject {string} taught by {string}")
    public void aTimetableEntryIsCreated(String studentClass, String section, String day,
                                         int period, String subject, String teacherAlias) {
        Map<String, Object> body = new HashMap<>();
        body.put("studentClass", studentClass);
        body.put("section", section);
        body.put("dayOfWeek", day);
        body.put("period", period);
        body.put("subject", subject);
        body.put("teacherId", world.teacherProfile(teacherAlias).getId().toString());
        world.exchange(HttpMethod.POST, "/api/v1/timetable", body);
    }

    @When("the timetable for class {string} section {string} is requested")
    public void theTimetableIsRequested(String studentClass, String section) {
        world.exchange(HttpMethod.GET, "/api/v1/timetable/" + studentClass + "/" + section, null);
    }
}
