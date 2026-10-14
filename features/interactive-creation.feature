Feature: Calm interactions and assisted creation
 Scenario: Time-based practice entry
  When a user selects five minutes on the start page
  Then the breathing practice opens with a five minute duration
  And the user can enable reduced motion without losing information

 Scenario: Safe AI line art
  Given the user has agreed to privacy and this generation request
  When the model returns bounded geometry parameters
  Then the app renders editable shapes without executing model markup
  When the provider fails or returns unsupported data
  Then the app explicitly labels the built-in fallback template

 Scenario: Camera permission and lifecycle
  Given the tracing page is open
  Then the camera is not requested until the user clicks the start button
  When permission is denied
  Then local image and blank-canvas modes remain usable
  When a late permission response arrives after cancellation
  Then its tracks are stopped immediately
  And leaving the page stops all active camera tracks

 Scenario: Material relationship browsing
  When a user filters by a material type
  Then only matching published projects are shown
  And missing safety metadata is shown as unverified
