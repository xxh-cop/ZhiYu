package com.zhiyu.modules.emotion.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话 VO（T1.3a）。
 */
@Data
@AllArgsConstructor
public class SessionVO {

    private Long sessionId;
    private String mood;
    private LocalDateTime startTime;
}
