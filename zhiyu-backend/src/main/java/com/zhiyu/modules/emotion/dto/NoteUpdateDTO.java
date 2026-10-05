package com.zhiyu.modules.emotion.dto;

import lombok.Data;

/**
 * 编辑心情便签请求体（M3-A1）。
 *
 * <p>对应 {@code PUT /api/emotion/notes/{id}}。
 * 仅更新非 null 字段。
 */
@Data
public class NoteUpdateDTO {

    /** 情绪 code，null 表示不更新 */
    private String mood;

    /** 便签内容（明文），null 表示不更新；非空则重新加密 */
    private String content;
}
