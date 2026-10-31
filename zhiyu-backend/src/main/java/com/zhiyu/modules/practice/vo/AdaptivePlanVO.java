package com.zhiyu.modules.practice.vo;

import java.util.List;

/** 规则驱动的自适应练习计划；不读取情绪原文，不作情绪或疗效判断。 */
public record AdaptivePlanVO(
        int minutes,
        String levelBias,
        double completionRate,
        int completedCount,
        int abandonedCount,
        String source,
        boolean historyUsed,
        boolean generated,
        String reason,
        List<Item> items) {

    public record Item(
            String patternId,
            String name,
            String traditionName,
            String family,
            String familyLabel,
            int minutes,
            String level,
            String learningFocus,
            List<String> palette,
            String reason) {
    }
}
