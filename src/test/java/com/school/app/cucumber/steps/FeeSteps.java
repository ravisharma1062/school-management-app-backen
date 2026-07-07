package com.school.app.cucumber.steps;

import com.school.app.cucumber.support.World;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.HashMap;
import java.util.Map;

public class FeeSteps {

    @Autowired
    private World world;

    @When("fees for student {string} are requested")
    public void feesAreRequested(String studentAlias) {
        world.exchange(HttpMethod.GET,
                "/api/v1/fees/student/" + world.student(studentAlias).getId(), null);
    }

    @When("fee {string} is updated with amount paid {string}")
    public void feeIsUpdatedWithAmountPaid(String feeAlias, String amountPaid) {
        Map<String, Object> body = new HashMap<>();
        body.put("amountPaid", amountPaid);
        world.exchange(HttpMethod.PATCH, "/api/v1/fees/" + world.fee(feeAlias).getId(), body);
    }

    @When("fee {string} is updated with status {string}")
    public void feeIsUpdatedWithStatus(String feeAlias, String status) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        world.exchange(HttpMethod.PATCH, "/api/v1/fees/" + world.fee(feeAlias).getId(), body);
    }
}
