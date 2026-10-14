Feature: Unified creative space with music and video
 Scenario: Voluntary music playback
  Given a user opens the music room
  Then no music starts automatically
  When the user starts a track and sets a timer
  Then the player shows playback progress
  And playback pauses when the user leaves the page

 Scenario: Network failure during upload
  Given a user selects a video within 50MB
  When the upload fails
  Then the selected file can be retried
  And no successful publication is shown

 Scenario: Reviewed video sharing
  Given a user uploads an MP4 video
  When the user publishes a public video work
  Then the work awaits review
  And another student cannot play it until an administrator approves it
  When the work is removed from publication
  Then previously issued playback links no longer grant access

 Scenario: Inheritor teaches through video
  Given an inheritor owns a pending project
  When the inheritor saves an uploaded teaching video with text steps
  And an administrator publishes the project
  Then students can watch the video on its detail and creation pages

 Scenario: Invalid upload or reference
  Given a file with a fake MP4 header or another user's video reference
  When a user attempts to upload or publish it
  Then the server rejects the request
