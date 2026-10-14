package com.zhiyu.modules.inheritor.dto;

import lombok.Data;

/**
 * 传承人编辑项目请求体（M2 批 5A）。
 *
 * <p>字段同 {@link ProjectCreateDTO}，但允许部分更新（所有字段可空）。
 */
@Data
public class ProjectUpdateDTO {

    private String title;
    private String intro;
    private String cultureBg;
    private Integer level;
    private Integer estMinutes;
    private String category;
    private String therapyTags;
    private String coverUrl;

    /** 平台上传的教学视频引用；与项目一起审核。 */
    private String videoUrl;
}
