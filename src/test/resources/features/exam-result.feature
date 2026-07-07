Feature: Exam results
  Teachers and admins record exam results (the grade is computed server-side);
  teachers and parents may read a student's results.

  Background:
    Given a user "teacher1" with role TEACHER
    And a user "parent1" with role PARENT
    And a user "parent2" with role PARENT
    And a student "child1" in class "5" section "A" with parent "parent1"

  Scenario: Teacher records a result and the grade is computed
    Given "teacher1" is logged in
    When an exam result is recorded for student "child1" subject "Math" exam "Midterm" scoring "95" out of "100" in term "Term 1"
    Then the request should succeed
    And the JSON field "/grade" should equal "A+"

  Scenario: A borderline score is graded correctly
    Given "teacher1" is logged in
    When an exam result is recorded for student "child1" subject "Math" exam "Midterm" scoring "75" out of "100" in term "Term 1"
    Then the request should succeed
    And the JSON field "/grade" should equal "B"

  Scenario: Parent cannot record a result
    Given "parent1" is logged in
    When an exam result is recorded for student "child1" subject "Math" exam "Midterm" scoring "95" out of "100" in term "Term 1"
    Then the request should be rejected as forbidden

  Scenario: Negative marks fail validation
    Given "teacher1" is logged in
    When an exam result is recorded for student "child1" subject "Math" exam "Midterm" scoring "-5" out of "100" in term "Term 1"
    Then the request should be rejected as a bad request

  Scenario: Parent can view their own child's results
    Given "teacher1" is logged in
    And an exam result is recorded for student "child1" subject "Math" exam "Midterm" scoring "95" out of "100" in term "Term 1"
    Given "parent1" is logged in
    When exam results for student "child1" are requested
    Then the request should succeed
    And the response should be a non-empty array

  Scenario: Parent cannot view another parent's child's results
    Given "parent2" is logged in
    When exam results for student "child1" are requested
    Then the request should be rejected as forbidden
