package com.zhiyu.ai.metrics;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 调用用量与质量指标记录器（BO 批，2026-11-05）。
 *
 * <p>落库载体：复用已有 {@code ai_trace}（独立指标表，脱敏元数据、只追加）。
 * 不新建表、不改 {@code ai_audit_log}（审计语义为安全过滤事件，粒度不同）。
 *
 * <p><b>旁路异步</b>：单线程 daemon executor + 单条 INSERT，写库失败仅记 warn 消化，
 * 绝不抛出影响业务响应（模式同 {@code CrisisSessionLogService.recordAsync}）。
 *
 * <p><b>不记明文</b>：只写资源 key（scene）、模型标识、延迟、结果状态、
 * 降级/熔断原因与 token 用量；不记录任何对话原文、prompt、工具参数。
 * tokens 只有 {@code chatWithUsage} 类调用拿得到；拿不到一律留 {@code null}，不估算。
 *
 * <p>结果状态词表（{@code result_status}）：
 * <ul>
 *   <li>{@link #OK} —— 模型产出可用（输出被安全改写仍算 OK，原因记 {@link #OUTPUT_REWRITE}）</li>
 *   <li>{@link #FALLBACK} —— 业务降级（熔断拒绝 / 提供方异常 / 空结果 / Mock 环境 / 输出被拒）</li>
 *   <li>{@link #BLOCKED} —— 输入侧安全拦截，未发起模型调用</li>
 *   <li>{@link #ERROR} —— 调用失败并以错误形式抛给调用方（chat SSE、asr/tts）</li>
 * </ul>
 * 降级/熔断原因词表（{@code fallback_reason}）：{@link #BREAKER_OPEN} 熔断拒绝、
 * {@link #PROVIDER_ERROR} 提供方异常或空结果、{@link #MOCK_DISABLED} 未配置真实模型、
 * {@link #INPUT_BLOCK} 输入拦截、{@link #OUTPUT_REJECTED} 输出未过终检、
 * {@link #OUTPUT_REWRITE} 输出被改写、{@link #SPEECH_UNAVAILABLE} 语音未配置。
 */
@Slf4j
@Service
public class AiCallMetrics {

    public static final String OK = "OK";
    public static final String FALLBACK = "FALLBACK";
    public static final String BLOCKED = "BLOCKED";
    public static final String ERROR = "ERROR";

    public static final String BREAKER_OPEN = "BREAKER_OPEN";
    public static final String PROVIDER_ERROR = "PROVIDER_ERROR";
    public static final String MOCK_DISABLED = "MOCK_DISABLED";
    public static final String INPUT_BLOCK = "INPUT_BLOCK";
    public static final String OUTPUT_REJECTED = "OUTPUT_REJECTED";
    public static final String OUTPUT_REWRITE = "OUTPUT_REWRITE";
    public static final String SPEECH_UNAVAILABLE = "SPEECH_UNAVAILABLE";

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate db;
    private final ExecutorService worker;
    private final boolean enabled;

    /** Spring 生产装配（存在私有构造函数，需显式 @Autowired 指定注入点）。 */
    @org.springframework.beans.factory.annotation.Autowired
    public AiCallMetrics(JdbcTemplate db) {
        this(db, true);
    }

    private AiCallMetrics(JdbcTemplate db, boolean enabled) {
        this.db = db;
        this.enabled = enabled;
        this.worker = enabled ? Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ai-metrics-writer");
            t.setDaemon(true);
            return t;
        }) : null;
    }

    /** 无操作实例：测试用旧签名构造 AiClient 时占位，保持既有测试语义不变。 */
    public static AiCallMetrics noop() {
        return new AiCallMetrics(null, false);
    }

    /**
     * 是否真正落库。调用方应在 {@code false} 时短路整个埋点（包括模型名等参数求值），
     * 保证 no-op 实例下对被测协作者零交互（兼容既有 {@code verifyNoInteractions} 语义）。
     */
    public boolean active() {
        return enabled;
    }

    @PreDestroy
    void shutdown() {
        if (worker != null) {
            worker.shutdown();
        }
    }

    /**
     * 旁路记录一次 AI 调用指标（异步、失败吞掉）。
     *
     * @param userId         当前用户（调用线程内取好，不依赖 worker 线程 ThreadLocal）
     * @param scene          资源 key：llm-chat / llm-recommend / llm-message / llm-strong /
     *                       llm-moderation / llm-summary / llm-rag / llm-lineart / asr / tts
     * @param model          模型标识（真实模型名 / "mock" / 语音适配器类名）；未知留 null
     * @param latencyMs      调用耗时（熔断拒绝、输入拦截等未发起调用的为 ~0）
     * @param status         OK / FALLBACK / BLOCKED / ERROR
     * @param fallbackReason 降级/熔断原因，无则 null
     * @param inputTokens    入参 tokens；客户端拿不到留 null（不估算）
     * @param outputTokens   出参 tokens；同上
     */
    public void record(Long userId, String scene, String model, long latencyMs,
                       String status, String fallbackReason,
                       Integer inputTokens, Integer outputTokens) {
        if (!enabled) {
            return;
        }
        try {
            worker.execute(() -> write(userId, scene, model, latencyMs, status,
                    fallbackReason, inputTokens, outputTokens));
        } catch (RuntimeException e) {
            log.warn("AI 指标提交失败（不影响业务响应）: {}", e.getMessage());
        }
    }

    /** 同步写入（异步 worker 内执行；测试可直接调用）。 */
    void write(Long userId, String scene, String model, long latencyMs,
               String status, String fallbackReason, Integer inputTokens, Integer outputTokens) {
        try {
            db.update("""
                    INSERT INTO ai_trace(id, trace_id, user_id, scene, model,
                        latency_ms, input_tokens, output_tokens, result_status, fallback_reason, create_time)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?)
                    """, IdWorker.getId(), UUID.randomUUID().toString(), userId,
                    cut(scene, 30), cut(model, 80), Math.max(0, latencyMs),
                    inputTokens, outputTokens, cut(status, 20), cut(fallbackReason, 120),
                    LocalDateTime.now(ZONE));
        } catch (Exception e) {
            log.warn("AI 指标写入失败（已消化，不影响业务响应）: {}", e.getMessage());
        }
    }

    private String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}
