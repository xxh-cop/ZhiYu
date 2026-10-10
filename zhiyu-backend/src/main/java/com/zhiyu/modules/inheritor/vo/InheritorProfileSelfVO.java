package com.zhiyu.modules.inheritor.vo;

import lombok.Data;

/**
 * 传承人自己的资料返回体（批次 6-A）。
 *
 * <p>用于 {@code GET /api/inheritor/profile} 和 {@code PUT /api/inheritor/profile}。
 */
@Data
public class InheritorProfileSelfVO {

    /** inheritor_profile 主键 */
    private Long id;

    /** 关联用户 ID */
    private Long userId;

    /** 擅长技艺 */
    private String skill;

    /** 个人简介 */
    private String intro;

    /** 荣誉头衔 */
    private String honor;

    /** 0 未认证 / 1 已认证 */
    private Integer verified;

    /** 头像 URL（来自 user 表） */
    private String avatarUrl;
}
