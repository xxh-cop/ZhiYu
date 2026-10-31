package com.zhiyu.modules.companion;

import java.util.List;

/** 一轮陪伴的完整结果；generated=false 表示使用确定性降级，而不是模型生成。 */
public record CompanionTurn(List<CompanionEvent> events, String reply, CompanionStage stage,
                            boolean generated, boolean blocked, boolean toolUsed, String toolName) {

    public CompanionTurn {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
