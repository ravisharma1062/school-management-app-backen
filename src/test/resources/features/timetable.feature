Feature: Timetable
  Admins create weekly timetable entries; admins, teachers and parents may view them.

  Background:
    Given a user "teacher1" with role TEACHER
    And a user "parent1" with role PARENT

  Scenario: Admin creates a timetable entry
    Given the admin is logged in
    When a timetable entry is created for class "5" section "A" on "MONDAY" period 1 subject "Math" taught by "teacher1"
    Then the request should succeed
    And the JSON field "/subject" should equal "Math"

  Scenario: Teacher cannot create a timetable entry
    Given "teacher1" is logged in
    When a timetable entry is created for class "5" section "A" on "MONDAY" period 1 subject "Math" taught by "teacher1"
    Then the request should be rejected as forbidden

  Scenario: An out-of-range period fails validation
    Given the admin is logged in
    When a timetable entry is created for class "5" section "A" on "MONDAY" period 99 subject "Math" taught by "teacher1"
    Then the request should be rejected as a bad request

  Scenario: Parent can view a class timetable
    Given the admin is logged in
    And a timetable entry is created for class "5" section "A" on "MONDAY" period 1 subject "Math" taught by "teacher1"
    Given "parent1" is logged in
    When the timetable for class "5" section "A" is requested
    Then the request should succeed
    And the response should be a non-empty array

  Scenario: Viewing a timetable requires authentication
    Given the client is not authenticated
    When the timetable for class "5" section "A" is requested
    Then the request should be rejected as unauthenticated
