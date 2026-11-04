package com.zhiyu.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 项目下架/删除理由（AY1/AY2，2026-11-05）。必填，落 craft_project.audit_reason 并随通知送达传承人。
 */
@Data
public class ProjectTakedownDTO {

    @NotBlank(message = "必须填写理由")
    @Size(max = 300, message = "理由不超过 300 字")
    private String reason;
}
