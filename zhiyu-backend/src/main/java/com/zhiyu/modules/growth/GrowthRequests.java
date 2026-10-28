package com.zhiyu.modules.growth;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class GrowthRequests {
    private GrowthRequests() {}
    public record Mood(@NotNull @Min(1) @Max(5) Integer mood, @Size(max=500) String note, @AssertTrue boolean consent) {}
    public record Assessment(@NotBlank String scale, @NotBlank String version,
        @NotNull @Size(max=20) List<@NotNull Integer> answers, @AssertTrue boolean consent, boolean draft) {}
    public record Reflection(@NotNull Long sessionId, @NotBlank @Size(max=100) String title,
        @Min(1) @Max(5) Integer beforeMood, @NotNull @Min(1) @Max(5) Integer afterMood,
        @Size(max=30) String emotion, @Size(max=500) String note,
        @Size(max=300) String summary, @AssertTrue boolean consent) {
        public Reflection(Long sessionId, String title, Integer beforeMood, Integer afterMood,
                          String emotion, String note, boolean consent) {
            this(sessionId, title, beforeMood, afterMood, emotion, note, null, consent);
        }
    }
    public record ReflectionSummary(@NotNull Long sessionId, @NotBlank @Size(max=100) String title,
        @Min(1) @Max(5) Integer beforeMood, @NotNull @Min(1) @Max(5) Integer afterMood,
        @Size(max=30) String emotion, @Size(max=500) String note, @AssertTrue boolean consent) {}
    public record Program(@NotBlank @Pattern(regexp="WORKSHOP|COURSE|GUIDANCE") String kind,
        @NotBlank @Size(max=100) String title, @NotBlank @Size(max=6000) String description,
        @NotBlank @Size(max=200) String location, @Size(max=100) String coLeader,
        @Size(max=1000) String materials, @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime, @Min(1) @Max(200) int capacity,
        @NotBlank @Pattern(regexp="DRAFT|PUBLISHED|CANCELLED") String status) {}
    public record Checkin(@NotNull Long bookingId) {}
    public record Safety(@NotBlank @Pattern(regexp="染|塑|织绣|纸|香|雕") String medium,
        @NotBlank @Pattern(regexp="LOW|GUIDED|HIGH") String level,
        @NotBlank @Size(max=300) String hazards, @NotBlank @Size(max=1000) String guidance,
        @NotBlank @Size(max=300) String source) {}
}
