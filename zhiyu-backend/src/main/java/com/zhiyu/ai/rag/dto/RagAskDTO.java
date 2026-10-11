package com.zhiyu.ai.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * RAG 问答请求 DTO（M3-3）。
 */
@Data
public class RagAskDTO {

    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题长度不能超过 500 字")
    private String question;
}
