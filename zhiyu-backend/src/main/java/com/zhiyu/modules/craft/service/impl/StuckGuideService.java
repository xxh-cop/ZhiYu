package com.zhiyu.modules.craft.service.impl;

import com.zhiyu.ai.AiClient;
import com.zhiyu.modules.craft.entity.CraftSession;
import com.zhiyu.modules.craft.enums.CraftSessionEventType;
import com.zhiyu.modules.craft.service.CraftSessionEventService;
import com.zhiyu.security.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 卡点引导（stuck guidance，2026-11-05，设计稿 docs/stuck-guidance-design-20261105.md）。
 *
 * <p>触发：心跳派生信号 {@code DWELL_STAGNATION}（同一步骤 dwell 反复回落超阈值；
 * 纯挂机 dwell 单调递增永不触发，真沉浸不误伤）。本服务只消费信号，
 * 不改 {@code FlowDetectionService} 任何判定行。
 *
 * <p>链路（不打断心跳主链路）：
 * <ol>
 *   <li>{@link #onStagnation}：开关（user.stuck_guide_enabled）→ 频率上限
 *       （每会话 / 每用户每日，证据 = craft_session_event 的 stuck_guide_shown 行）
 *       → 落触点事件 → 异步生成引导语（复用 llm-message 熔断档）；</li>
 *   <li>{@link #poll}：后续心跳取回文案。AI 就绪用 AI 文案；未就绪超
 *       {@code zhiyu.stuck-guide.async-timeout-ms}（默认 5 秒）或生成失败，
 *       一律降级 {@link #TEMPLATE} 静态文案——用户永远收到完整可用的提示；</li>
 *   <li>心跳响应延迟预算维持现状：生成在 worker 线程，主链路只读写内存 Map。</li>
 * </ol>
 *
 * <p>产品红线：轻提示、不阻塞、可一键收起（前端形态）、可永久关闭
 * （{@link #setEnabled} 写 user.stuck_guide_enabled）。文案不做诊断、
 * 不判定用户状态，只给可选动作建议。
 */
@Slf4j
@Service
public class StuckGuideService {

    /** 静态兜底文案：非诊断、给可选动作、明示可收起。AI 缺席时用户看到的就是它。 */
    public static final String TEMPLATE =
            "这一步可以慢一点，也可以先看看示例或别人的做法。这条提示随时可以收起。";

    /** 触点事件类型：频率上限的计数证据（设计稿 §4「无新表，事件沿用 craft_session_event」）。 */
    static final String EVENT_VALUE = "stuck_guide_shown";

    private final JdbcTemplate jdbc;
    private final AiClient aiClient;
    private final CraftSessionEventService eventService;
    private final String promptTemplate;
    private final Executor executor;
    private final int sessionMax;
    private final int dailyMax;
    private final long asyncTimeoutMs;

    /** 待下发的引导语（sessionId → 生成中的 future）；纯内存，会话结束随进程生命周期回收。 */
    private final Map<Long, Pending> pending = new ConcurrentHashMap<>();

    private static final class Pending {
        final long createdAt = System.currentTimeMillis();
        final CompletableFuture<String> future = new CompletableFuture<>();
    }

    @Autowired
    public StuckGuideService(JdbcTemplate jdbc,
                             AiClient aiClient,
                             CraftSessionEventService eventService,
                             @Value("classpath:prompts/stuck-guide.st") Resource promptResource,
                             @Value("${zhiyu.stuck-guide.session-max:2}") int sessionMax,
                             @Value("${zhiyu.stuck-guide.daily-max:3}") int dailyMax,
                             @Value("${zhiyu.stuck-guide.async-timeout-ms:5000}") long asyncTimeoutMs)
            throws java.io.IOException {
        this(jdbc, aiClient, eventService, readPrompt(promptResource),
                Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "stuck-guide-worker");
                    t.setDaemon(true);
                    return t;
                }),
                sessionMax, dailyMax, asyncTimeoutMs);
    }

    /** 全参构造（单测可注入同步 executor 与阈值）。 */
    public StuckGuideService(JdbcTemplate jdbc, AiClient aiClient, CraftSessionEventService eventService,
                             String promptTemplate, Executor executor,
                             int sessionMax, int dailyMax, long asyncTimeoutMs) {
        this.jdbc = jdbc;
        this.aiClient = aiClient;
        this.eventService = eventService;
        this.promptTemplate = promptTemplate;
        this.executor = executor;
        this.sessionMax = sessionMax;
        this.dailyMax = dailyMax;
        this.asyncTimeoutMs = asyncTimeoutMs;
    }

    /** 显式 UTF-8 读 prompt（AGENTS.md §10：不得依赖 file.encoding）。 */
    private static String readPrompt(Resource resource) throws java.io.IOException {
        try (var in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * DWELL_STAGNATION 触发入口（心跳线程内调用，自身快速返回，生成在 worker 线程）。
     * 任何一步不满足都静默返回（不打扰是默认行为）。
     */
    public void onStagnation(CraftSession session, Integer stepNo, String projectTitle) {
        Long sessionId = session.getId();
        Long userId = session.getUserId();
        evictStale();
        if (pending.containsKey(sessionId)) {
            return;
        }
        if (!isEnabled(userId)) {
            log.debug("卡点引导已关闭，跳过: userId={}", userId);
            return;
        }
        long sessionCount = countEvents("session_id=?", sessionId);
        if (sessionCount >= sessionMax) {
            log.debug("卡点引导会话级频率上限: sessionId={}, count={}", sessionId, sessionCount);
            return;
        }
        long userToday = countEvents("user_id=? AND occurred_at >= CURRENT_DATE", userId);
        if (userToday >= dailyMax) {
            log.debug("卡点引导用户日级频率上限: userId={}, count={}", userId, userToday);
            return;
        }

        Pending p = new Pending();
        if (pending.putIfAbsent(sessionId, p) != null) {
            return;
        }
        // 触点证据事件（频率上限计数来源）；失败只记日志，不影响引导下发
        eventService.recordServerEvent(sessionId, userId, session.getProjectId(),
                CraftSessionEventType.STUCK_GUIDE_SHOWN, stepNo,
                Map.of("stepNo", stepNo), null);

        String prompt = promptTemplate
                .replace("{projectTitle}", projectTitle == null ? "" : projectTitle)
                .replace("{stepNo}", String.valueOf(stepNo));
        executor.execute(() -> {
            try {
                p.future.complete(aiClient.stuckGuide(userId, prompt));
            } catch (Throwable t) {
                log.warn("卡点引导异步生成异常（降级模板）: sessionId={}, err={}", sessionId, t.getMessage());
                p.future.complete(null);
            }
        });
        log.info("卡点引导触点: sessionId={}, userId={}, stepNo={}", sessionId, userId, stepNo);
    }

    /**
     * 心跳收尾时取回待下发文案（每次心跳至多一条，取出即消费）。
     *
     * @return 可展示文案（AI 或静态模板）；无待下发或尚未就绪返回 null
     */
    public String poll(Long sessionId) {
        Pending p = pending.get(sessionId);
        if (p == null) {
            return null;
        }
        if (p.future.isDone()) {
            pending.remove(sessionId, p);
            String text = p.future.getNow(null);
            return (text == null || text.isBlank()) ? TEMPLATE : text;
        }
        if (System.currentTimeMillis() - p.createdAt >= asyncTimeoutMs) {
            pending.remove(sessionId, p);
            log.info("卡点引导生成超时，降级静态模板: sessionId={}", sessionId);
            return TEMPLATE;
        }
        return null;
    }

    /** 用户级总开关查询（设置页）。 */
    public boolean isEnabled(Long userId) {
        try {
            Integer v = jdbc.queryForObject(
                    "SELECT stuck_guide_enabled FROM user WHERE id=?", Integer.class, userId);
            return v != null && v == 1;
        } catch (EmptyResultDataAccessException e) {
            return false;
        }
    }

    /** 用户级总开关设置（永久关闭/重新开启）。 */
    public void setEnabled(Long userId, boolean enabled) {
        jdbc.update("UPDATE user SET stuck_guide_enabled=?, update_time=CURRENT_TIMESTAMP WHERE id=?",
                enabled ? 1 : 0, userId);
        log.info("卡点引导开关设置: userId={}, enabled={}", userId, enabled);
    }

    /** 当前用户开关查询（Controller 用）。 */
    public boolean currentEnabled() {
        return isEnabled(UserContext.getUserId());
    }

    /** 当前用户开关设置（Controller 用）。 */
    public void setCurrentEnabled(boolean enabled) {
        setEnabled(UserContext.getUserId(), enabled);
    }

    private long countEvents(String where, Object arg) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM craft_session_event WHERE deleted=0"
                        + " AND event_type='" + EVENT_VALUE + "' AND " + where,
                Long.class, arg);
        return n == null ? 0 : n;
    }

    /** 惰性清理：超过 30 分钟未取走的 pending（用户已离开会话）直接丢弃。 */
    private void evictStale() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> now - e.getValue().createdAt > 30 * 60_000L);
    }
}
