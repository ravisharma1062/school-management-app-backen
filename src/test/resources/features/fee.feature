Feature: Fees
  Admins view and update fee records; parents may view only their own child's fees.
  The fee status is derived from the amount paid unless set explicitly.

  Background:
    Given a user "teacher1" with role TEACHER
    And a user "parent1" with role PARENT
    And a user "parent2" with role PARENT
    And a student "child1" in class "9" section "A" with parent "parent1"
    And a pending fee "fee1" of 1000.00 for student "child1"

  Scenario: Parent can view their own child's fees
    Given "parent1" is logged in
    When fees for student "child1" are requested
    Then the request should succeed
    And the response should be a non-empty array

  Scenario: Parent cannot view another parent's child's fees
    Given "parent2" is logged in
    When fees for student "child1" are requested
    Then the request should be rejected as forbidden

  Scenario: Admin can view a student's fees
    Given the admin is logged in
    When fees for student "child1" are requested
    Then the request should succeed

  Scenario: Teacher cannot view fees
    Given "teacher1" is logged in
    When fees for student "child1" are requested
    Then the request should be rejected as forbidden

  Scenario: Paying a fee in full derives the PAID status
    Given the admin is logged in
    When fee "fee1" is updated with amount paid "1000.00"
    Then the request should succeed
    And the JSON field "/status" should equal "PAID"

  Scenario: Paying part of a fee derives the PARTIAL status
    Given the admin is logged in
    When fee "fee1" is updated with amount paid "400.00"
    Then the request should succeed
    And the JSON field "/status" should equal "PARTIAL"

  Scenario: Overpaying a fee is rejected
    Given the admin is logged in
    When fee "fee1" is updated with amount paid "5000.00"
    Then the request should be rejected as a bad request

  Scenario: Admin can set a fee status explicitly
    Given the admin is logged in
    When fee "fee1" is updated with status "OVERDUE"
    Then the request should succeed
    And the JSON field "/status" should equal "OVERDUE"

  Scenario: Parent cannot update a fee
    Given "parent1" is logged in
    When fee "fee1" is updated with amount paid "500.00"
    Then the request should be rejected as forbidden
