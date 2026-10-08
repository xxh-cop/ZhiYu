package com.zhiyu.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiyu.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户表实体，对应 {@code sql/schema.sql} 的 {@code user} 表。
 *
 * <p>id / createTime / updateTime / deleted 由 {@link BaseEntity} 提供，
 * 主键策略为应用侧雪花（{@code IdType.ASSIGN_ID}），DDL 不设 AUTO_INCREMENT。
 *
 * <p>注意：{@code user} 是 H2 的保留字，单测环境的 JDBC URL 必须带
 * {@code NON_KEYWORDS=USER}（见 {@code sql/schema-h2.sql}）；
 * 生产库是 MySQL，{@code user} 不是保留字，故本实体不做任何特殊处理。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    /** 登录名（学号或昵称），唯一 */
    private String username;

    /** BCrypt 密码哈希，绝不存明文 */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatarUrl;

    /** 所在学校 */
    private String school;

    /** 个人简介 */
    private String intro;

    /** 手作偏好标签，逗号分隔 */
    private String craftPreferences;

    /** 0 不允许 1 允许心理咨询师联系 */
    private Integer canContact;

    /** 隐私政策是否同意：0 未同意 1 已同意 */
    private Integer privacyAgreed;

    /** 角色码：0 学生 1 传承人 2 咨询师 3 管理员，见 AGENTS.md §3.1 */
    private Integer role;

    /** 0 禁用 1 正常 */
    private Integer status;
}
