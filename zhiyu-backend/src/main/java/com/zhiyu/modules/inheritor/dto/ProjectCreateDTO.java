package com.zhiyu.modules.inheritor.dto;

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

    @NotNull(message = "level 不能为空")
    private Integer level;

    private Integer estMinutes;

    private String category;

    private String therapyTags;

    private String coverUrl;

    /** 平台上传的教学视频引用；与项目一起审核。 */
    private String videoUrl;
}
