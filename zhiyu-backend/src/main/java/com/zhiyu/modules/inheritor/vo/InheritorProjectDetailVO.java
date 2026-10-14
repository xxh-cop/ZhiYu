package com.zhiyu.modules.inheritor.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 传承人项目详情 VO（M2 批 5A）。
 */
@Data
public class InheritorProjectDetailVO {

    private Long id;
    private Long inheritorId;
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
    private Integer status;
    private LocalDateTime createTime;

    /** 步骤列表 */
    private List<StepVO> steps;

    /** 材料列表 */
    private List<MaterialVO> materials;

    /**
     * 步骤 VO（内嵌）。
     */
    @Data
    public static class StepVO {
        private Long id;
        private Integer stepNo;
        private String title;
        private String guide;
        private String audioUrl;
    }
}
