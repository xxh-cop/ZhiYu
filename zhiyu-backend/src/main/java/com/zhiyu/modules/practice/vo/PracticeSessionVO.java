package com.zhiyu.modules.practice.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PracticeSessionVO {

    private Long practiceSessionId;
    private String kind;
    private Integer status;
    private String statusLabel;
    private String moodCode;
    private Integer minutesBudget;
    private Long emotionSessionId;
    private Long craftSessionId;
    private String heritagePatternId;
    private Integer currentStep;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
}
