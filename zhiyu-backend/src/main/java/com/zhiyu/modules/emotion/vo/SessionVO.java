package com.zhiyu.modules.emotion.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话 VO（T1.3a）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionVO {

    private Long sessionId;
    private String mood;
    private LocalDateTime startTime;
    /** 会话总结（解密后明文，进行中为 null） */
    private String summary;
    /** 0 进行中 / 1 已结束 */
    private Integer status;
}
