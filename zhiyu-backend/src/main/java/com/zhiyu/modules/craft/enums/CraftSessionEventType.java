package com.zhiyu.modules.craft.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * 创作会话行为事件类型（2026-11-05 心流度量配套）。
 *
 * <p>字符串值入库 {@code craft_session_event.event_type}，全小写下划线。
 *
 * <p>焦躁双信号分开记录、不合并：{@link #DWELL_STAGNATION}（同一步骤反复停留）
 * 与 {@link #STEP_BACKTRACK}（步骤回退）。
 *
 * <p>{@link #CLIENT_ALLOWED}：允许经 POST /api/craft/session/{id}/events 上报的类型；
 * 其余类型只能由服务端派生写入，客户端上报一律 1001。
 */
public enum CraftSessionEventType {

    /** 步骤进入 */
    STEP_ENTER("step_enter"),
    /** 步骤离开（context/dwell_seconds 携带本步骤最终停留秒数） */
    STEP_LEAVE("step_leave"),
    /** 步骤推进（前进） */
    STEP_ADVANCE("step_advance"),
    /** 步骤回退（焦躁信号之一，既有口径） */
    STEP_BACKTRACK("step_backtrack"),
    /** 心跳（客户端批量补传通道；服务端心跳主流程不逐条落库） */
    HEARTBEAT("heartbeat"),
    /** 音频播放（沉浸强信号之一） */
    AUDIO_PLAY("audio_play"),
    /** 页面可见性变化（context.visible=true/false） */
    VISIBILITY_CHANGE("visibility_change"),
    /** 应用前后台切换（context.foreground=true/false，对应 onAppShow/onAppHide） */
    APP_LIFECYCLE("app_lifecycle"),
    /** 同一步骤反复停留超阈值（焦躁信号之二；仅服务端派生） */
    DWELL_STAGNATION("dwell_stagnation"),
    /** 疑似离席标记（只标记，不从 flow_seconds 扣除；仅服务端派生） */
    SUSPECTED_AWAY("suspected_away"),
    /** 呼吸引导触发（仅服务端派生） */
    BREATHING_PROMPT("breathing_prompt"),
    /** 呼吸引导响应 */
    BREATHING_RESPONDED("breathing_responded"),
    /** 作品导出（下架窗口内导出等） */
    EXPORT("export"),
    /** 会话完成（结束钩子；仅服务端派生） */
    SESSION_FINISH("session_finish"),
    /** 会话放弃（结束钩子；仅服务端派生） */
    SESSION_ABANDON("session_abandon"),
    /** 项目归档导致会话转只读（结束钩子；仅服务端派生） */
    SESSION_ARCHIVE("session_archive"),
    /**
     * 心跳/推进被下架闸口拒绝（仅服务端派生，2026-11-05）。
     * 只读起点的精确证据（被拒那一刻），看板剔除判定的第三档优先消费本事件，
     * 无本事件时兜底用 session_archive。每会话至多一条（只读单调不可逆，幂等）。
     */
    STEP_GATE_BLOCKED("step_gate_blocked");

    private final String value;

    CraftSessionEventType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /** 允许客户端上报的事件类型。STEP_GATE_BLOCKED 为服务端证据事件，客户端不可上报（防伪造拒绝证据）。 */
    public static final Set<CraftSessionEventType> CLIENT_ALLOWED = EnumSet.of(
            STEP_ENTER, STEP_LEAVE, STEP_ADVANCE, STEP_BACKTRACK,
            HEARTBEAT, AUDIO_PLAY, VISIBILITY_CHANGE, APP_LIFECYCLE,
            BREATHING_RESPONDED, EXPORT);

    /**
     * 按入库字符串反查枚举。
     *
     * @param value 事件类型字符串
     * @return 对应枚举；未知值返回 {@code null}
     */
    public static CraftSessionEventType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (CraftSessionEventType t : values()) {
            if (t.value.equals(value)) {
                return t;
            }
        }
        return null;
    }
}
