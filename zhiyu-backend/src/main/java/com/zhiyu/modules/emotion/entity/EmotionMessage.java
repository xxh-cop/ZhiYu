package com.zhiyu.modules.emotion.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiyu.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 对话消息实体（T1.3a）。
 *
 * <p>对应 {@code emotion_message} 表（见 sql/schema.sql）。
 * {@code content} 列存加密后的密文（AES-256-GCM），由 {@link com.zhiyu.common.crypto.AesEncryptor} 加解密。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emotion_message")
public class EmotionMessage extends BaseEntity {

    /** 所属会话 emotion_session.id */
    private Long sessionId;

    /** 消息角色：{@code user} / {@code assistant} */
    private String role;

    /** 消息内容（加密存储，密文 Base64） */
    private String content;
}
