package com.zhiyu.modules.work.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 评论请求 DTO（T1.8a）。
 */
@Data
public class CommentReq {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 255, message = "评论不能超过 255 字")
    private String content;
}
