package com.zhiyu.modules.inheritor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 传承人创建/编辑项目请求体（M2 批 5A）。
 */
@Data
public class ProjectCreateDTO {

    @NotBlank(message = "title 不能为空")
    private String title;

    private String intro;

    private String cultureBg;

    /** 难度等级：1 入门 / 2 进阶 / 3 高阶（与 craft_project.level DDL 注释一致） */
    @NotNull(message = "level 不能为空")
    @Min(value = 1, message = "level 取值范围为 1-3")
    @Max(value = 3, message = "level 取值范围为 1-3")
    private Integer level;

    private Integer estMinutes;

    private String category;

    private String therapyTags;

    private String coverUrl;

    /** 平台上传的教学视频引用；与项目一起审核。 */
    private String videoUrl;
}
