package com.zhiyu.modules.practice.enums;

/** 统一练习会话状态；只允许明确的状态流转，非法流转返回 3001。 */
public enum PracticeSessionStatus {
    CREATED(0, "已创建"),
    LISTENING(1, "倾听中"),
    RECOMMENDED(2, "已推荐"),
    PRACTICING(3, "练习中"),
    PAUSED(4, "已暂停"),
    COMPLETED(5, "已完成"),
    ARTIFACT_SAVED(6, "已保存"),
    ABANDONED(7, "已放弃");

    private final int code;
    private final String label;

    PracticeSessionStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean canTransitTo(PracticeSessionStatus target) {
        if (target == null) return false;
        return switch (this) {
            case CREATED -> target == LISTENING || target == PRACTICING || target == ABANDONED;
            case LISTENING -> target == RECOMMENDED || target == PRACTICING || target == ABANDONED;
            case RECOMMENDED -> target == PRACTICING || target == ABANDONED;
            case PRACTICING -> target == PAUSED || target == COMPLETED || target == ABANDONED;
            case PAUSED -> target == PRACTICING || target == ABANDONED;
            case COMPLETED -> target == ARTIFACT_SAVED;
            case ARTIFACT_SAVED -> false;
            case ABANDONED -> target == PRACTICING;
        };
    }

    public static PracticeSessionStatus fromCode(Integer code) {
        if (code == null) return null;
        for (PracticeSessionStatus status : values()) {
            if (status.code == code) return status;
        }
        return null;
    }

    public static PracticeSessionStatus targetForAction(String action) {
        if (action == null) return null;
        return switch (action.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "START_LISTENING" -> LISTENING;
            case "RECOMMEND" -> RECOMMENDED;
            case "START", "RESUME" -> PRACTICING;
            case "PAUSE" -> PAUSED;
            case "COMPLETE" -> COMPLETED;
            case "SAVE_ARTIFACT" -> ARTIFACT_SAVED;
            case "ABANDON" -> ABANDONED;
            default -> null;
        };
    }
}
