Feature: Attendance
  Teachers mark attendance; teachers and parents may read a student's history.

  Background:
    Given a user "teacher1" with role TEACHER
    And a user "parent1" with role PARENT
    And a user "parent2" with role PARENT
    And a student "child1" in class "5" section "A" with parent "parent1"

  Scenario: Teacher marks a student present
    Given "teacher1" is logged in
    When attendance for student "child1" on "2026-07-01" is marked as "PRESENT"
    Then the request should succeed
    And the JSON field "/status" should equal "PRESENT"

  Scenario: Marking attendance twice updates the same record
    Given "teacher1" is logged in
    When attendance for student "child1" on "2026-07-01" is marked as "ABSENT"
    And attendance for student "child1" on "2026-07-01" is marked as "PRESENT"
    Then the request should succeed
    And the JSON field "/status" should equal "PRESENT"

  Scenario: Parent cannot mark attendance
    Given "parent1" is logged in
    When attendance for student "child1" on "2026-07-01" is marked as "PRESENT"
    Then the request should be rejected as forbidden

  Scenario: Parent can view their own child's attendance
    Given "teacher1" is logged in
    And attendance for student "child1" on "2026-07-01" is marked as "PRESENT"
    Given "parent1" is logged in
    When attendance history for student "child1" is requested
    Then the request should succeed
    And the response should be a non-empty array

  Scenario: Parent cannot view another parent's child's attendance
    Given "parent2" is logged in
    When attendance history for student "child1" is requested
    Then the request should be rejected as forbidden

  Scenario: Teacher can view a whole class on a date
    Given "teacher1" is logged in
    And attendance for student "child1" on "2026-07-01" is marked as "PRESENT"
    When attendance for class "5" section "A" on "2026-07-01" is requested
    Then the request should succeed
    And the response should be a non-empty array

  Scenario: Parent cannot view a whole class roster
    Given "parent1" is logged in
    When attendance for class "5" section "A" on "2026-07-01" is requested
    Then the request should be rejected as forbidden
