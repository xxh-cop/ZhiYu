package com.zhiyu.modules.emotion.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建心情便签请求体（M3-A1）。
 *
 * <p>对应 {@code POST /api/emotion/notes}。
 */
@Data
public class NoteCreateDTO {

    /** 情绪 code（不加密），可选 */
    private String mood;

    /** 便签内容（明文，Service 层加密存储） */
    @NotBlank(message = "内容不能为空")
    private String content;

    /** 记录日期（yyyy-MM-dd），可选，默认今天 */
    private String noteDate;
}
