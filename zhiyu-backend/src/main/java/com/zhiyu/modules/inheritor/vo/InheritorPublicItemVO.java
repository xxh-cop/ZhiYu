package com.zhiyu.modules.inheritor.vo;

import lombok.Data;

/**
 * 传承人公开列表项 VO（M2 批 6A）。
 *
 * <p>学生端列表页展示，只暴露非敏感字段。
 * {@code id} 为 {@code inheritor_profile.id}（不是 {@code user.id}）。
 */
@Data
public class InheritorPublicItemVO {

    /** inheritor_profile.id */
    private Long id;

    /** 关联 user.id */
    private Long userId;

    /** 昵称（user.nickname） */
    private String nickname;

    /** 擅长技艺 */
    private String skill;

    /** 荣誉头衔 */
    private String honor;

    /** 简介 */
    private String intro;

    /** 头像 URL（user.avatar_url） */
    private String avatarUrl;

    /** 已上架手作项目数（craft_project.status=1 AND deleted=0） */
    private Integer projectCount;
}
