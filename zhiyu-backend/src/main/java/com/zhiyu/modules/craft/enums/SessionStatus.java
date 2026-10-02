package com.zhiyu.modules.craft.enums;

/**
 * 创作会话状态枚举（T1.6）。
 *
 * <p>状态码与 {@code schema.sql} 的 {@code craft_session.status} 注释一致：
 * <ul>
 *   <li>{@link #PENDING}(0) —— 待开始（DDL 保留，应用层不产生）</li>
 *   <li>{@link #DOING}(1) —— 进行中</li>
 *   <li>{@link #PAUSED}(2) —— 已暂停</li>
 *   <li>{@link #FINISHED}(3) —— 已完成</li>
 *   <li>{@link #ABANDONED}(4) —— 已放弃</li>
 * </ul>
 *
 * <p><b>没有 start 动作</b>（A4 裁决）：创建会话时直接进入 {@link #DOING}，
 * 不经过 {@link #PENDING} 中间态。状态机只有 pause / resume / finish / abandon。
 */
public enum SessionStatus {

    PENDING(0, "待开始"),
    DOING(1, "进行中"),
    PAUSED(2, "已暂停"),
    FINISHED(3, "已完成"),
    ABANDONED(4, "已放弃");

    private final Integer code;
    private final String label;

    SessionStatus(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public Integer getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 按 code 反查枚举项。
     *
     * @param code 状态码
     * @return 对应枚举项；{@code null} 返回 {@code null}
     */
    public static SessionStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (SessionStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        return null;
    }

    /**
     * 判断从当前状态能否流转到 target。
     *
     * <p>允许的流转：
     * <ul>
     *   <li>DOING → PAUSED / FINISHED / ABANDONED</li>
     *   <li>PAUSED → DOING / ABANDONED</li>
     *   <li>PENDING / FINISHED / ABANDONED → 不可流转</li>
     * </ul>
     *
     * @param target 目标状态
     * @return 允许流转返回 true，否则 false
     */
    public boolean canTransitTo(SessionStatus target) {
        return switch (this) {
            case DOING -> target == PAUSED || target == FINISHED || target == ABANDONED;
            case PAUSED -> target == DOING || target == ABANDONED;
            case PENDING, FINISHED, ABANDONED -> false;
        };
    }
}
