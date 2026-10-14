Feature: Reliable operations dashboard and existing database upgrade
  Scenario: Administrator views actual activity
    Given an administrator and an upgraded database
    When the administrator selects a 30 day trend
    Then all 30 dates appear including zero activity dates
    And deleted records are excluded from aggregate totals

  Scenario: Student cannot read operations data
    Given an authenticated student
    When the student requests operations statistics
    Then access is denied

  Scenario: Existing database is missing media columns
    Given a legacy work table without media_type
    When the application starts
    Then startup reports the missing field and migration path
    When the additive migration is applied twice
    Then existing work records remain readable

  Scenario: Statistics request fails
    Given the operations dashboard is open
    When the statistics request fails
    Then an error with a retry action appears
    And stale statistics are not displayed as current data
