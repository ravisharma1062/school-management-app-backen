package com.school.app.cucumber.steps;

import com.school.app.cucumber.support.World;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.HashMap;
import java.util.Map;

public class ExamResultSteps {

    @Autowired
    private World world;

    @When("an exam result is recorded for student {string} subject {string} exam {string} scoring {string} out of {string} in term {string}")
    public void anExamResultIsRecorded(String studentAlias, String subject, String examName,
                                       String marks, String maxMarks, String term) {
        Map<String, Object> body = new HashMap<>();
        body.put("studentId", world.student(studentAlias).getId().toString());
        body.put("subject", subject);
        body.put("examName", examName);
        body.put("marksObtained", marks);
        body.put("maxMarks", maxMarks);
        body.put("term", term);
        world.exchange(HttpMethod.POST, "/api/v1/exam-results", body);
    }

    @When("exam results for student {string} are requested")
    public void examResultsAreRequested(String studentAlias) {
        world.exchange(HttpMethod.GET,
                "/api/v1/exam-results/student/" + world.student(studentAlias).getId(), null);
    }
}
