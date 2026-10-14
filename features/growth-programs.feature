Feature: Private growth records and capacity-safe programs
  Scenario: Save a daily mood with explicit consent
    Given the authenticated user has agreed to the privacy policy
    When they save two moods for the same day with consent
    Then only one encrypted daily record exists for this user
    And a different user cannot read or erase it

  Scenario: Safety signal in fixed assessment
    When a user selects a nonzero answer to PHQ-9 question nine
    Then the app immediately opens the existing support page
    And server submission creates a de-identified pending care reminder
    And no clinical diagnosis is returned

  Scenario: Concurrent last seat
    Given one seat remains in a published future program
    When two users book concurrently
    Then exactly one booking succeeds
    And cancellation makes the seat available again

  Scenario: Host-only check-in
    Given a participant has booked a program
    Then other participants cannot read the attendee list
    And the host can check in within the configured attendance window

  Scenario: Knowledge question safety boundary
    When an input is blocked by the safety filter
    Then neither embedding retrieval nor the answer model runs
    And generated answer output is audited before display
