package com.zhiyu.modules.work.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发布作品请求 DTO（T1.8a）。
 *
 * <p>对应 OpenAPI schema {@code WorkPublishReq}。
 */
@Data
public class WorkPublishReq {

    /** 关联创作会话 ID（可选） */
    private Long sessionId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题不能超过 100 字")
    private String title;

    /** 封面图 URL（可选，media_type=image 时必填） */
    private String coverUrl;

    /** 媒体类型：image/video/text（可选，默认 image） */
    private String mediaType;

    /** 视频 URL（可选，media_type=video 时必填） */
    private String videoUrl;

    /** AI 寄语（可选） */
    private String message;

    /** 0私密 1公开 2仅小组（可选，默认 1） */
    private Integer visible;

    /** 是否匿名（可选，默认 true） */
    private Boolean anonymous;
}
