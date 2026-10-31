package com.zhiyu.modules.companion;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 只追加的 AI 运行元数据。调用方只能传归一化字段，
 * 不传用户原文、思维链、工具参数或检索片段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanionTraceService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate db;

    public void record(Long userId, Long practiceSessionId, String scene, String model,
                       String promptVersion, String toolName, int latencyMs,
                       String resultStatus, String fallbackReason,
                       Integer inputTokens, Integer outputTokens) {
        try {
            db.update("""
                    INSERT INTO ai_trace(id, trace_id, user_id, practice_session_id, scene, model,
                        prompt_version, tool_name, latency_ms, input_tokens, output_tokens,
                        result_status, fallback_reason, create_time)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, IdWorker.getId(), UUID.randomUUID().toString(), userId, practiceSessionId,
                    cut(scene, 30), cut(model, 80), cut(promptVersion, 40), cut(toolName, 60),
                    Math.max(0, latencyMs), inputTokens, outputTokens,
                    cut(resultStatus, 20), cut(fallbackReason, 120),
                    LocalDateTime.now(ZONE));
        } catch (Exception e) {
            log.warn("AI trace 写入失败，已忽略: {}", e.getMessage());
        }
    }

    public void record(Long userId, Long practiceSessionId, String scene, String model,
                       String promptVersion, String toolName, int latencyMs,
                       String resultStatus, String fallbackReason) {
        record(userId, practiceSessionId, scene, model, promptVersion, toolName, latencyMs,
                resultStatus, fallbackReason, null, null);
    }

    private String cut(String value, int max) {
        if (value == null) return null;
        String text = value.trim();
        if (text.isEmpty()) return null;
        return text.length() > max ? text.substring(0, max) : text;
    }
}
