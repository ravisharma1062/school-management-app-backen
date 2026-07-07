Feature: Authentication
  Users authenticate with email/password and receive JWT access and refresh tokens.

  Scenario: The seeded admin can log in
    When a login is attempted with email "admin@school.app" and password "Admin@123"
    Then the request should succeed
    And the JSON field "/accessToken" should not be empty
    And the JSON field "/refreshToken" should not be empty
    And the JSON field "/role" should equal "ADMIN"

  Scenario: A parent logs in and receives their role
    Given a user "parent1" with role PARENT
    When "parent1" attempts to log in with password "Password@123"
    Then the request should succeed
    And the JSON field "/role" should equal "PARENT"

  Scenario: Login with the wrong password is rejected
    Given a user "parent1" with role PARENT
    When "parent1" attempts to log in with password "wrong-password"
    Then the request should be rejected as unauthorized

  Scenario: Login with an unknown email is rejected
    When a login is attempted with email "nobody@school.app" and password "whatever"
    Then the request should be rejected as unauthorized

  Scenario: An authenticated user can read their own profile
    Given a user "teacher1" with role TEACHER
    And "teacher1" is logged in
    When the current user's profile is requested
    Then the request should succeed
    And the JSON field "/role" should equal "TEACHER"

  Scenario: The profile endpoint requires authentication
    Given the client is not authenticated
    When the current user's profile is requested
    Then the request should be rejected as unauthenticated

  Scenario: A valid refresh token can be exchanged for a new token pair
    Given a user "parent1" with role PARENT
    And "parent1" is logged in
    When the stored refresh token is exchanged
    Then the request should succeed
    And the JSON field "/accessToken" should not be empty

  Scenario: An invalid refresh token is rejected
    When an invalid refresh token is exchanged
    Then the request should be rejected as unauthorized
