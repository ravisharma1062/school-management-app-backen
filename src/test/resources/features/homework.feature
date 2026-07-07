Feature: Homework
  Teachers post homework for a class/section; admins, teachers and parents may read it.

  Background:
    Given a user "teacher1" with role TEACHER
    And a user "parent1" with role PARENT

  Scenario: Teacher posts homework
    Given "teacher1" is logged in
    When homework is posted for class "5" section "A" subject "Science" titled "Read chapter 3" due "2027-01-10"
    Then the request should succeed
    And the JSON field "/title" should equal "Read chapter 3"

  Scenario: Admin cannot post homework
    Given the admin is logged in
    When homework is posted for class "5" section "A" subject "Science" titled "Blocked" due "2027-01-10"
    Then the request should be rejected as forbidden

  Scenario: Homework with a past due date fails validation
    Given "teacher1" is logged in
    When homework is posted for class "5" section "A" subject "Science" titled "Late" due "2020-01-10"
    Then the request should be rejected as a bad request

  Scenario: Parent can read homework for a class
    Given "teacher1" is logged in
    And homework is posted for class "5" section "A" subject "Science" titled "Read chapter 3" due "2027-01-10"
    Given "parent1" is logged in
    When homework for class "5" section "A" is requested
    Then the request should succeed
    And the paged response should contain 1 item

  Scenario: Reading homework requires authentication
    Given the client is not authenticated
    When homework for class "5" section "A" is requested
    Then the request should be rejected as unauthenticated
