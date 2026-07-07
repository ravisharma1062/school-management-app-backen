package com.school.app.cucumber.steps;

import com.school.app.cucumber.support.World;
import com.school.app.user.User;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.Map;

/**
 * Steps for the auth endpoints (login/refresh/me) that we assert on directly, as opposed to the
 * convenience "is logged in" precondition in {@link CommonSteps}.
 */
public class AuthSteps {

    @Autowired
    private World world;

    @When("{string} attempts to log in with password {string}")
    public void attemptsToLogInWithPassword(String alias, String password) {
        User user = world.user(alias);
        world.clearAuthentication();
        world.exchange(HttpMethod.POST, "/api/v1/auth/login",
                Map.of("email", user.getEmail(), "password", password));
    }

    @When("a login is attempted with email {string} and password {string}")
    public void aLoginIsAttempted(String email, String password) {
        world.clearAuthentication();
        world.exchange(HttpMethod.POST, "/api/v1/auth/login",
                Map.of("email", email, "password", password));
    }

    @When("the current user's profile is requested")
    public void theCurrentUsersProfileIsRequested() {
        world.exchange(HttpMethod.GET, "/api/v1/auth/me", null);
    }

    @When("the stored refresh token is exchanged")
    public void theStoredRefreshTokenIsExchanged() {
        world.clearAuthentication();
        world.exchange(HttpMethod.POST, "/api/v1/auth/refresh",
                Map.of("refreshToken", world.var("refreshToken")));
    }

    @When("an invalid refresh token is exchanged")
    public void anInvalidRefreshTokenIsExchanged() {
        world.clearAuthentication();
        world.exchange(HttpMethod.POST, "/api/v1/auth/refresh",
                Map.of("refreshToken", "not-a-real-token"));
    }
}
