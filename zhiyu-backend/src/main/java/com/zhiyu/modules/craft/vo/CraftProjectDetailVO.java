package com.zhiyu.modules.craft.vo;

import com.zhiyu.modules.inheritor.vo.MaterialVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 手作项目公开详情 VO（M2 批 6A）。
 *
 * <p>学生端项目详情页展示，包含项目全部公开字段 + 传承人信息 + 步骤 + 材料。
 */
@Data
public class CraftProjectDetailVO {

    // ── 项目基础字段 ──

    /** 项目 ID */
    private Long id;

    /** 项目标题 */
    private String title;

    /** 项目简介 */
    private String intro;

    /** 文化背景 */
    private String cultureBg;

    /** 1 入门 / 2 进阶 / 3 高阶 */
    private Integer level;

    /** 预计时长（分钟） */
    private Integer estMinutes;

    /** 技艺品类 */
    private String category;

    /** 疗愈效果标签，逗号分隔 */
    private String therapyTags;

    /** 封面图 URL */
    private String coverUrl;

    /** 创建时间 */
    private LocalDateTime createTime;

    // ── 传承人信息 ──

    /** 传承人 user.id */
    private Long inheritorId;

    /** 传承人昵称 */
    private String inheritorNickname;

    /** 传承人擅长技艺 */
    private String inheritorSkill;

    // ── 关联数据 ──

    /** 步骤列表 */
    private List<CraftStepVO> steps;

    /** 材料列表 */
    private List<MaterialVO> materials;
}
