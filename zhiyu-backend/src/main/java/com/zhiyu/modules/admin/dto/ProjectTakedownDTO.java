package com.zhiyu.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目下架请求（AY1/AY2 2026-11-05 理由必填；2026-11-05 分层下架增加类型与截止时间）。
 *
 * <p>{@code kind}：compliance（合规下架，导出窗口由系统配置）/ operational（运营下架，
 * 必须给 {@code archiveAt} 截止时间，且距现在至少 7 天）。类型与截止时间的细化校验
 * 在 {@code AdminServiceImpl#takedownProject}（要算「距现在」的相对时间，DTO 注解表达不了）。
 */
@Data
public class ProjectTakedownDTO {

    @NotBlank(message = "必须填写理由")
    @Size(max = 300, message = "理由不超过 300 字")
    private String reason;

    /** 下架类型：compliance / operational（必填，缺省拒绝） */
    private String kind;

    /** 归档截止时间（ISO-8601，如 2026-11-20T10:00:00）；运营下架必填，合规下架不得提供 */
    private LocalDateTime archiveAt;
}
