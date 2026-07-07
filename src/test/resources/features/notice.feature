Feature: Notices
  Admins post notices targeted at a role; all authenticated users read the notices visible to them.

  Background:
    Given a user "teacher1" with role TEACHER
    And a user "parent1" with role PARENT

  Scenario: Admin posts a notice
    Given the admin is logged in
    When a notice titled "Holiday" targeting "ALL" is posted
    Then the request should succeed
    And the JSON field "/title" should equal "Holiday"

  Scenario: Teacher cannot post a notice
    Given "teacher1" is logged in
    When a notice titled "Blocked" targeting "ALL" is posted
    Then the request should be rejected as forbidden

  Scenario: A parent's notice feed includes ALL notices but excludes teacher-only notices
    Given the admin is logged in
    And a notice titled "Holiday" targeting "ALL" is posted
    And a notice titled "Staff meeting" targeting "TEACHER" is posted
    Given "parent1" is logged in
    When notices are requested
    Then the request should succeed
    And the paged response should contain 1 item

  Scenario: A teacher's notice feed includes teacher-targeted and ALL notices
    Given the admin is logged in
    And a notice titled "Holiday" targeting "ALL" is posted
    And a notice titled "Staff meeting" targeting "TEACHER" is posted
    Given "teacher1" is logged in
    When notices are requested
    Then the request should succeed
    And the paged response should contain 2 items

  Scenario: Listing notices requires authentication
    Given the client is not authenticated
    When notices are requested
    Then the request should be rejected as unauthenticated
