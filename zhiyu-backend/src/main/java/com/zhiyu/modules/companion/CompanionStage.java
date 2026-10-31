package com.zhiyu.modules.companion;

import java.util.Locale;

/** 受控陪伴阶段；模型只能输出其中之一，越界由编排层拒绝。 */
public enum CompanionStage {
    LISTEN("先听你说"),
    RECOMMEND("准备练习推荐"),
    PRACTICE("一起做练习"),
    BREATHE("呼吸一下"),
    DONE("收个尾");

    private final String label;

    CompanionStage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static CompanionStage from(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
