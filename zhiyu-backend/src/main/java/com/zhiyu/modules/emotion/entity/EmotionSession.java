package com.zhiyu.modules.emotion.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiyu.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 情绪会话实体（T1.3a）。
 *
 * <p>对应 {@code emotion_session} 表（见 sql/schema.sql）。
 * 一个会话代表学生的一次情绪倾听对话流。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emotion_session")
public class EmotionSession extends BaseEntity {

    /** 所属学生 user.id */
    private Long userId;

    /** 触发情绪 code，取值见 dict_mood.code */
    private String mood;

    /** 0 进行中 / 1 已结束 */
    private Integer status;
}
