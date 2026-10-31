package com.zhiyu.modules.practice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiyu.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 统一练习会话：把情绪、非遗练习、正念/绘色等模式挂到同一次练习。
 * context_cipher 只存加密后的限制与偏好，不存原始对话。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("practice_session")
public class PracticeSession extends BaseEntity {

    private Long userId;
    private String kind;
    private Integer status;
    private String moodCode;
    private Integer minutesBudget;
    private Long emotionSessionId;
    private Long craftSessionId;
    private String heritagePatternId;
    private Integer currentStep;
    private String contextCipher;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
