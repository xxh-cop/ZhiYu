package com.zhiyu.modules.companion;

import com.zhiyu.modules.practice.vo.PracticeSessionVO;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 前端可消费的受控事件，对应路线图 4.5 的 typed SSE。
 * 只暴露阶段、回复、工具结果与引用；不包含思考过程、工具原始参数或用户原文。
 */
public record CompanionEvent(String event, Object data) {

    public static final String STAGE = "stage";
    public static final String MESSAGE = "message";
    public static final String TOOL = "tool";
    public static final String CITATION = "citation";
    public static final String ARTIFACT = "artifact";
    public static final String DONE = "done";
    public static final String ERROR = "error";

    public static CompanionEvent stage(CompanionStage stage, PracticeSessionVO session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stage", stage.name());
        data.put("label", stage.getLabel());
        data.put("practiceSessionId", session.getPracticeSessionId() == null
                ? null : String.valueOf(session.getPracticeSessionId()));
        data.put("sessionStatus", session.getStatus());
        data.put("statusLabel", session.getStatusLabel());
        return new CompanionEvent(STAGE, data);
    }

    public static CompanionEvent message(String text, boolean generated) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("delta", text);
        data.put("generated", generated);
        return new CompanionEvent(MESSAGE, data);
    }

    public static CompanionEvent tool(String name, Object result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("status", "done");
        data.put("result", result);
        return new CompanionEvent(TOOL, data);
    }

    public static CompanionEvent citation(Object source) {
        return new CompanionEvent(CITATION, source);
    }

    public static CompanionEvent done(CompanionStage stage, PracticeSessionVO session,
                                      boolean generated, boolean blocked, boolean toolUsed) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("practiceSessionId", session.getPracticeSessionId() == null
                ? null : String.valueOf(session.getPracticeSessionId()));
        data.put("sessionStatus", session.getStatus());
        data.put("statusLabel", session.getStatusLabel());
        data.put("stage", stage.name());
        data.put("generated", generated);
        data.put("blocked", blocked);
        data.put("toolUsed", toolUsed);
        return new CompanionEvent(DONE, data);
    }
}
