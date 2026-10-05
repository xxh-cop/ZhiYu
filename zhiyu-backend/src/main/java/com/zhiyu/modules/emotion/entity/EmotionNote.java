package com.zhiyu.modules.emotion.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiyu.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 心情便签实体（M3-A1）。
 *
 * <p>对应 {@code emotion_note} 表（见 sql/schema.sql）。
 * content 字段在 Service 层用 {@link com.zhiyu.common.crypto.AesEncryptor} 加密存储，
 * 仅本人可见（AGENTS.md §5 / FR-10-03）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emotion_note")
public class EmotionNote extends BaseEntity {

    /** 所属学生 user.id */
    private Long userId;

    /** 心情便签内容（密文，AES-256-GCM） */
    private String content;

    /** 情绪 code，取值见 dict_mood.code（不加密） */
    private String mood;

    /** 记录日期 */
    private LocalDate noteDate;
}
