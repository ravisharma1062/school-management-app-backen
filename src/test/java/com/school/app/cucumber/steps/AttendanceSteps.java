package com.school.app.cucumber.steps;

import com.school.app.cucumber.support.World;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.HashMap;
import java.util.Map;

public class AttendanceSteps {

    @Autowired
    private World world;

    @When("attendance for student {string} on {string} is marked as {string}")
    public void attendanceIsMarked(String studentAlias, String date, String status) {
        Map<String, Object> body = new HashMap<>();
        body.put("studentId", world.student(studentAlias).getId().toString());
        body.put("date", date);
        body.put("status", status);
        world.exchange(HttpMethod.POST, "/api/v1/attendance", body);
    }

    @When("attendance history for student {string} is requested")
    public void attendanceHistoryIsRequested(String studentAlias) {
        world.exchange(HttpMethod.GET,
                "/api/v1/attendance/student/" + world.student(studentAlias).getId(), null);
    }

    @When("attendance for class {string} section {string} on {string} is requested")
    public void classAttendanceIsRequested(String studentClass, String section, String date) {
        world.exchange(HttpMethod.GET,
                "/api/v1/attendance/class/" + studentClass + "/" + section + "/" + date, null);
    }
}
