Feature: Student directory
  Admins manage the student directory; teachers may read it; parents may only see their own child.

  Background:
    Given a user "teacher1" with role TEACHER
    And a user "parent1" with role PARENT
    And a user "parent2" with role PARENT
    And a student "child1" in class "5" section "A" with parent "parent1"

  Scenario: Admin creates a student
    Given the admin is logged in
    When a student is created with name "New Student" roll "R-1001" class "6" section "B" dob "2014-05-05"
    Then the request should succeed
    And the JSON field "/name" should equal "New Student"

  Scenario: Teacher cannot create a student
    Given "teacher1" is logged in
    When a student is created with name "Blocked" roll "R-1002" class "6" section "B" dob "2014-05-05"
    Then the request should be rejected as forbidden

  Scenario: Parent cannot create a student
    Given "parent1" is logged in
    When a student is created with name "Blocked" roll "R-1003" class "6" section "B" dob "2014-05-05"
    Then the request should be rejected as forbidden

  Scenario: Creating a student is rejected without authentication
    Given the client is not authenticated
    When a student is created with name "Blocked" roll "R-1004" class "6" section "B" dob "2014-05-05"
    Then the request should be rejected as unauthenticated

  Scenario: Creating a student with a blank name fails validation
    Given the admin is logged in
    When a student is created with a blank name and roll "R-1005" class "6" section "B" dob "2014-05-05"
    Then the request should be rejected as a bad request

  Scenario: Admin can list students
    Given the admin is logged in
    When the student list is requested
    Then the request should succeed

  Scenario: Teacher can list students
    Given "teacher1" is logged in
    When the student list is requested
    Then the request should succeed

  Scenario: Parent cannot list students
    Given "parent1" is logged in
    When the student list is requested
    Then the request should be rejected as forbidden

  Scenario: Parent can view their own child
    Given "parent1" is logged in
    When student "child1" is fetched by id
    Then the request should succeed

  Scenario: Parent cannot view another parent's child
    Given "parent2" is logged in
    When student "child1" is fetched by id
    Then the request should be rejected as forbidden

  Scenario: Admin can update a student
    Given the admin is logged in
    When student "child1" is renamed to "Renamed Child"
    Then the request should succeed
    And the JSON field "/name" should equal "Renamed Child"

  Scenario: Teacher cannot update a student
    Given "teacher1" is logged in
    When student "child1" is renamed to "Hacked Name"
    Then the request should be rejected as forbidden
