package com.zhiyu.modules.craft.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.craft.dto.SessionEventBatchReq;
import com.zhiyu.modules.craft.dto.SessionEventItem;
import com.zhiyu.modules.craft.entity.CraftSession;
import com.zhiyu.modules.craft.entity.CraftSessionEvent;
import com.zhiyu.modules.craft.enums.CraftSessionEventType;
import com.zhiyu.modules.craft.enums.SessionStatus;
import com.zhiyu.modules.craft.mapper.CraftSessionEventMapper;
import com.zhiyu.modules.craft.mapper.CraftSessionMapper;
import com.zhiyu.modules.craft.service.CraftSessionEventService;
import com.zhiyu.modules.craft.vo.SessionEventBatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 创作会话行为事件服务实现（2026-11-05 心流度量配套）。
 *
 * <p>幂等：客户端事件按 {@code (session_id, client_seq)} 唯一键去重，
 * 重复序号命中 {@link DuplicateKeyException} → 计 deduped，不算错误。
 *
 * <p>脱敏：{@code context} 只允许标量键值（数字/布尔/≤64 字符短字符串），
 * 嵌套结构、超长字符串、超大序列化结果一律整体 1001——宁缺毋滥，
 * 不给对话或自由文本留入口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CraftSessionEventServiceImpl implements CraftSessionEventService {

    /** context 键数量上限 */
    private static final int CONTEXT_MAX_KEYS = 20;
    /** context 键/字符串值长度上限 */
    private static final int CONTEXT_MAX_STRING = 64;
    /** context 序列化长度上限 */
    private static final int CONTEXT_MAX_BYTES = 2048;
    /** 客户端时钟偏移容忍（发生时刻不得超前服务端超过该值） */
    private static final Duration CLOCK_SKEW_TOLERANCE = Duration.ofMinutes(5);
    /** dwellSeconds 取值上限（24 小时，防脏数据） */
    private static final int DWELL_MAX_SECONDS = 86400;

    private final CraftSessionEventMapper eventMapper;
    private final CraftSessionMapper craftSessionMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void recordServerEvent(Long sessionId, Long userId, Long projectId,
                                  CraftSessionEventType type, Integer stepNo,
                                  Map<String, Object> context, Integer dwellSeconds) {
        try {
            CraftSessionEvent event = buildEvent(sessionId, userId, projectId,
                    type, stepNo, LocalDateTime.now(), null, context, dwellSeconds);
            eventMapper.insert(event);
        } catch (Exception e) {
            // 事件是观测数据，失败不得影响业务主流程
            log.warn("服务端派生事件落库失败（已忽略）: sessionId={}, type={}, err={}",
                    sessionId, type.getValue(), e.getMessage());
        }
    }

    /**
     * 独立事务写入（2026-11-05 闸口证据事件）：REQUIRES_NEW 挂起外层事务独立提交，
     * 使「业务以异常收尾」（如 3001 闸口拒绝回滚）时事件仍存活。
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordServerEventStandalone(Long sessionId, Long userId, Long projectId,
                                            CraftSessionEventType type, Integer stepNo,
                                            Map<String, Object> context, Integer dwellSeconds) {
        recordServerEvent(sessionId, userId, projectId, type, stepNo, context, dwellSeconds);
    }

    @Override
    public SessionEventBatchResult ingest(Long userId, Long sessionId, SessionEventBatchReq req) {
        // 1. 会话存在 + 所有权
        CraftSession session = craftSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        // 2. 状态：进行中/已暂停可上报（暂停期仍可能有可见性/前后台事件）；其余 3001
        SessionStatus status = SessionStatus.fromCode(session.getStatus());
        if (status != SessionStatus.DOING && status != SessionStatus.PAUSED) {
            throw new BusinessException(ResultCode.BAD_STATE.getCode(),
                    "会话当前状态不允许上报事件: " + (status != null ? status.getLabel() : "未知"));
        }

        // 3. 全量预校验（契约错误整体 1001，不部分受理）
        List<LocalDateTime> parsedTimes = new java.util.ArrayList<>(req.getEvents().size());
        for (SessionEventItem item : req.getEvents()) {
            CraftSessionEventType type = CraftSessionEventType.fromValue(item.getEventType());
            if (type == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                        "未知事件类型: " + item.getEventType());
            }
            if (!CraftSessionEventType.CLIENT_ALLOWED.contains(type)) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                        "事件类型不允许客户端上报: " + item.getEventType());
            }
            validateContext(item.getContext());
            parsedTimes.add(parseOccurredAt(item.getOccurredAt()));
        }

        // 4. 逐条落库：重复序号去重、时钟超前丢弃，其余异常计 failed 不中断批次
        int accepted = 0;
        int deduped = 0;
        int failed = 0;
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < req.getEvents().size(); i++) {
            SessionEventItem item = req.getEvents().get(i);
            LocalDateTime occurredAt = parsedTimes.get(i);
            if (occurredAt.isAfter(now.plus(CLOCK_SKEW_TOLERANCE))) {
                failed++;
                continue;
            }
            CraftSessionEventType type = CraftSessionEventType.fromValue(item.getEventType());
            try {
                CraftSessionEvent event = buildEvent(sessionId, userId, session.getProjectId(),
                        type, item.getStepNo(), occurredAt, item.getClientSeq(),
                        item.getContext(), extractDwellSeconds(type, item.getContext()));
                eventMapper.insert(event);
                accepted++;
            } catch (DuplicateKeyException e) {
                deduped++;
            } catch (Exception e) {
                failed++;
                log.warn("客户端事件落库失败: sessionId={}, clientSeq={}, err={}",
                        sessionId, item.getClientSeq(), e.getMessage());
            }
        }
        return new SessionEventBatchResult(accepted, deduped, failed);
    }

    // ──────────────────────────────────────────────────────
    //  内部方法
    // ──────────────────────────────────────────────────────

    /**
     * 宽容解析发生时刻：先按带偏移的 RFC3339（如前端 toISOString() 的 ...Z），
     * 再退到无时区的 ISO 本地时间；无法解析整体 1001。
     */
    private LocalDateTime parseOccurredAt(String raw) {
        try {
            return LocalDateTime.ofInstant(
                    java.time.format.DateTimeFormatter.ISO_DATE_TIME.parse(raw, java.time.Instant::from),
                    java.time.ZoneId.systemDefault());
        } catch (java.time.DateTimeException ignored) {
            // 无偏移量的本地时间格式，走下面的分支
        }
        try {
            return LocalDateTime.parse(raw, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (java.time.format.DateTimeParseException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "occurredAt 无法解析: " + raw);
        }
    }

    private CraftSessionEvent buildEvent(Long sessionId, Long userId, Long projectId,
                                         CraftSessionEventType type, Integer stepNo,
                                         LocalDateTime occurredAt, Long clientSeq,
                                         Map<String, Object> context, Integer dwellSeconds) {
        CraftSessionEvent event = new CraftSessionEvent();
        event.setSessionId(sessionId);
        event.setUserId(userId);
        event.setCraftProjectId(projectId);
        event.setEventType(type.getValue());
        event.setStepNo(stepNo);
        event.setOccurredAt(occurredAt);
        event.setClientSeq(clientSeq);
        event.setContext(serializeContext(context));
        event.setDwellSeconds(dwellSeconds);
        return event;
    }

    /**
     * context 脱敏校验：仅标量键值。违规整体 1001。
     */
    private void validateContext(Map<String, Object> context) {
        if (context == null) {
            return;
        }
        if (context.size() > CONTEXT_MAX_KEYS) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "context 键数量超限");
        }
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.length() > CONTEXT_MAX_STRING) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "context 键非法");
            }
            Object value = entry.getValue();
            if (value == null || value instanceof Number || value instanceof Boolean) {
                continue;
            }
            if (value instanceof String s && s.length() <= CONTEXT_MAX_STRING) {
                continue;
            }
            // 嵌套结构/数组/长字符串：可能夹带自由文本，拒绝
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "context 只允许标量键值（数字/布尔/≤64 字符字符串）");
        }
    }

    private String serializeContext(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(context);
            if (json.length() > CONTEXT_MAX_BYTES) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "context 序列化超限");
            }
            return json;
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "context 无法序列化");
        }
    }

    /**
     * step_leave 的停留秒数从 context.dwellSeconds 提取成列（P95 统计用）。
     * 非数字或越界视为无该列。
     */
    private Integer extractDwellSeconds(CraftSessionEventType type, Map<String, Object> context) {
        if (type != CraftSessionEventType.STEP_LEAVE || context == null) {
            return null;
        }
        Object raw = context.get("dwellSeconds");
        if (raw instanceof Number n) {
            int value = n.intValue();
            if (value >= 0 && value <= DWELL_MAX_SECONDS) {
                return value;
            }
        }
        return null;
    }
}
