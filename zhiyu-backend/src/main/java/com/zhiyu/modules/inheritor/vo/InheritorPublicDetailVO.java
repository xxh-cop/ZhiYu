package com.zhiyu.modules.inheritor.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 传承人公开详情 VO（M2 批 6A）。
 *
 * <p>学生端传承人主页展示，在 {@link InheritorPublicItemVO} 基础上
 * 额外暴露 {@code school}（user.school）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InheritorPublicDetailVO extends InheritorPublicItemVO {

    /** 所在学校（user.school） */
    private String school;
}
