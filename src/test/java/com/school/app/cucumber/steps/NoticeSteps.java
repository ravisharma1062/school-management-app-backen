package com.school.app.cucumber.steps;

import com.school.app.cucumber.support.World;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.HashMap;
import java.util.Map;

public class NoticeSteps {

    @Autowired
    private World world;

    @When("a notice titled {string} targeting {string} is posted")
    public void aNoticeIsPosted(String title, String targetRole) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", "Notice body");
        body.put("targetRole", targetRole);
        world.exchange(HttpMethod.POST, "/api/v1/notices", body);
    }

    @When("notices are requested")
    public void noticesAreRequested() {
        world.exchange(HttpMethod.GET, "/api/v1/notices", null);
    }

    @When("notices targeting {string} are requested")
    public void noticesTargetingAreRequested(String targetRole) {
        world.exchange(HttpMethod.GET, "/api/v1/notices?role=" + targetRole, null);
    }
}
