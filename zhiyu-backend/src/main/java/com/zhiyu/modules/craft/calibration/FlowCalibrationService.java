package com.zhiyu.modules.craft.calibration;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.craft.entity.CraftSession;
import com.zhiyu.modules.craft.mapper.CraftSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 心流自评校准（阶段 1-1，2026-11-05）。
 *
 * <p>看板「心流」此前无任何效度锚点；本服务在会话 finish 时按配置比例抽样，
 * 向被抽中用户发单题自评（1~7），用 Spearman 等级相关检验「自评 vs 专注停留占比」，
 * 效度不足（ρ&lt;0.3 或样本&lt;30）时管理端指标名降级为「专注停留占比」。
 *
 * <p><b>存储映射（复用 wellbeing_record，不建新表）</b>：
 * {@code kind='FLOW_CAL'}，{@code record_key=会话 ID 字符串}——(user_id,kind,record_key)
 * 唯一键恰好给出「每人每会话一条」，抽样结论与作答落同一行，天然稳定可追溯；
 * {@code payload} 为加密 JSON（与既有敏感记录同口径），字段
 * {sampled, sampledAt, answered, skipped, score, answeredAt}。
 * 代价：score 在加密 JSON 里，统计需逐行解密（校准样本量小，可接受；管理员聚合按
 * update_time 倒序限量解密）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowCalibrationService {

    /** wellbeing_record.kind 取值 */
    public static final String KIND = "FLOW_CAL";
    /** 单题题干（契约同步；前端直接展示，不作疗效表述） */
    public static final String QUESTION = "刚才这段创作时间里，你的专注与沉浸程度如何？";
    /** 量表下限/上限（1~7 七点李克特：比 1~5 分辨率高，比 0~10 负担小，序数口径配 Spearman） */
    public static final int SCALE_MIN = 1;
    public static final int SCALE_MAX = 7;
    public static final String SCALE_MIN_LABEL = "完全没投入";
    public static final String SCALE_MAX_LABEL = "完全沉浸其中";
    /** 统计口径：样本不足该数一律 insufficient（不假装有结论） */
    public static final int MIN_STATS_SAMPLES = 30;
    /** 效度阈值：ρ ≥ 该值才可叫「心流占时」，否则降级「专注停留占比」 */
    public static final double VALIDITY_THRESHOLD = 0.3;
    /** 管理端聚合最多解密的最近记录数（样本量天然小，上限防失控） */
    private static final int STATS_SCAN_LIMIT = 5000;

    private final JdbcTemplate db;
    private final AesEncryptor crypto;
    private final ObjectMapper json;
    private final FlowCalibrationProperties props;
    private final CraftSessionMapper craftSessionMapper;

    // ── 抽样（finish 钩子，由 CraftSessionServiceImpl.changeStatus 调用） ──

    /**
     * 会话 finish 时抽样落库。已有记录（已抽中/已答/已跳过）直接返回——
     * 抽样结论一经落库不再改变（稳定 + 可追溯）。enabled=false 不抽样。
     * 唯一键冲突（并发重复 finish）视为已抽过，静默忽略。
     */
    public void onSessionFinished(CraftSession session) {
        if (!props.isEnabled()) {
            return;
        }
        long uid = session.getUserId();
        String key = String.valueOf(session.getId());
        Integer existing = db.queryForObject(
                "SELECT COUNT(*) FROM wellbeing_record WHERE user_id=? AND kind=? AND record_key=? AND deleted=0",
                Integer.class, uid, KIND, key);
        if (existing != null && existing > 0) {
            return;
        }
        boolean sampled = ThreadLocalRandom.current().nextDouble() < props.getSampleRate();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sampled", sampled);
        payload.put("sampledAt", LocalDateTime.now().toString());
        payload.put("answered", false);
        payload.put("skipped", false);
        payload.put("score", null);
        try {
            db.update("INSERT INTO wellbeing_record(id,user_id,kind,record_key,payload,create_time,update_time,deleted)"
                            + " VALUES(?,?,?,?,?,?,?,0)",
                    IdWorker.getId(), uid, KIND, key, encode(payload), LocalDateTime.now(), LocalDateTime.now());
            log.info("心流自评抽样: sessionId={}, userId={}, sampled={}", session.getId(), uid, sampled);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            log.info("心流自评抽样已存在（并发 finish 去重）: sessionId={}", session.getId());
        }
    }

    // ── 学生端查询/提交 ──

    /**
     * 查询校准状态。sampled=false 时前端不弹窗（记录不存在=未抽中/未 finish/功能关闭）。
     * 会话不存在 1004；非本人 1003。
     */
    public Map<String, Object> query(Long userId, Long sessionId) {
        requireOwnedSession(userId, sessionId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sampled", false);
        out.put("answered", false);
        out.put("skipped", false);
        out.put("score", null);
        Map<String, Object> payload = readPayload(userId, sessionId);
        if (payload == null || !Boolean.TRUE.equals(payload.get("sampled"))) {
            return out;
        }
        out.put("sampled", true);
        out.put("answered", Boolean.TRUE.equals(payload.get("answered")));
        out.put("skipped", Boolean.TRUE.equals(payload.get("skipped")));
        out.put("score", payload.get("score"));
        out.put("question", QUESTION);
        out.put("scale", Map.of("min", SCALE_MIN, "max", SCALE_MAX,
                "minLabel", SCALE_MIN_LABEL, "maxLabel", SCALE_MAX_LABEL));
        return out;
    }

    /**
     * 提交自评或跳过。跳过也落记录（区分「没看到」与「看到了不答」）；
     * 已作答后重复提交 3001；跳过之后仍允许补答（用户改变主意），反之不可。
     * 未被抽中（无记录或 sampled=false）→ 3001；评分越界 → 1001。
     *
     * @return 提交后的校准状态（与 {@link #query} 同形状）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submit(Long userId, Long sessionId, Integer score, boolean skip) {
        requireOwnedSession(userId, sessionId);
        Map<String, Object> payload = readPayload(userId, sessionId);
        if (payload == null || !Boolean.TRUE.equals(payload.get("sampled"))) {
            throw new BusinessException(ResultCode.BAD_STATE.getCode(), "该会话未被抽中自评校准");
        }
        if (Boolean.TRUE.equals(payload.get("answered"))) {
            throw new BusinessException(ResultCode.BAD_STATE.getCode(), "已提交过自评，不可重复作答");
        }
        if (skip && score == null) {
            payload.put("skipped", true);
        } else if (score != null) {
            if (score < SCALE_MIN || score > SCALE_MAX) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                        "评分需在 " + SCALE_MIN + "~" + SCALE_MAX + " 之间");
            }
            payload.put("answered", true);
            payload.put("score", score);
            payload.put("answeredAt", LocalDateTime.now().toString());
        } else {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请提交评分或选择跳过");
        }
        db.update("UPDATE wellbeing_record SET payload=?,update_time=? WHERE user_id=? AND kind=? AND record_key=? AND deleted=0",
                encode(payload), LocalDateTime.now(), userId, KIND, String.valueOf(sessionId));
        log.info("心流自评提交: sessionId={}, userId={}, answered={}", sessionId, userId, payload.get("answered"));
        return query(userId, sessionId);
    }

    // ── 管理端聚合（效度判定） ──

    /**
     * 自评 × 专注停留占比的 Spearman 相关。
     * 专注停留占比 = flow_seconds / 会话时长（秒），仅统计已作答且时长为正的会话。
     * 样本 &lt; 30 → status=insufficient、rho=null、指标名保守取「专注停留占比」；
     * ρ ≥ 0.3 → metricLabel=「心流占时」，否则降级「专注停留占比」。
     */
    public Map<String, Object> adminStats() {
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT record_key,payload FROM wellbeing_record WHERE kind=? AND deleted=0"
                        + " ORDER BY update_time DESC LIMIT ?", KIND, STATS_SCAN_LIMIT);
        List<Double> scores = new ArrayList<>();
        List<Double> ratios = new ArrayList<>();
        for (var row : rows) {
            Map<String, Object> payload = decode(String.valueOf(row.get("payload")));
            if (!Boolean.TRUE.equals(payload.get("answered")) || payload.get("score") == null) {
                continue;
            }
            Long sessionId;
            try {
                sessionId = Long.valueOf(String.valueOf(row.get("record_key")));
            } catch (NumberFormatException e) {
                continue;
            }
            var sessions = db.queryForList(
                    "SELECT flow_seconds,start_time,end_time FROM craft_session WHERE id=? AND deleted=0", sessionId);
            if (sessions.isEmpty()) {
                continue;
            }
            var s = sessions.get(0);
            // MySQL Connector/J 8.0.23+ 对 DATETIME 返回 LocalDateTime，H2 返回 Timestamp——两类都收
            LocalDateTime start = toLdt(s.get("start_time"));
            LocalDateTime end = toLdt(s.get("end_time"));
            if (start == null || end == null) {
                continue;
            }
            long duration = Duration.between(start, end).getSeconds();
            if (duration <= 0) {
                continue;
            }
            double flow = s.get("flow_seconds") == null ? 0 : ((Number) s.get("flow_seconds")).doubleValue();
            scores.add(((Number) payload.get("score")).doubleValue());
            ratios.add(flow / duration);
        }
        int n = scores.size();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("method", "spearman");
        out.put("threshold", VALIDITY_THRESHOLD);
        out.put("minSamples", MIN_STATS_SAMPLES);
        out.put("sampleSize", n);
        if (n < MIN_STATS_SAMPLES) {
            out.put("status", "insufficient");
            out.put("rho", null);
            // 无效度证据时一律保守命名（行为描述），不得假装有结论
            out.put("metricLabel", "专注停留占比");
            return out;
        }
        double rho = Spearman.between(
                scores.stream().mapToDouble(Double::doubleValue).toArray(),
                ratios.stream().mapToDouble(Double::doubleValue).toArray());
        out.put("status", "ok");
        out.put("rho", rho);
        out.put("metricLabel", rho >= VALIDITY_THRESHOLD ? "心流占时" : "专注停留占比");
        return out;
    }

    // ── 内部 ──

    /** H2（Timestamp）与 MySQL（LocalDateTime，Connector/J 8.0.23+）时间类型兼容。 */
    private static LocalDateTime toLdt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        return ((java.sql.Timestamp) value).toLocalDateTime();
    }

    private CraftSession requireOwnedSession(Long userId, Long sessionId) {
        CraftSession session = craftSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return session;
    }

    private Map<String, Object> readPayload(Long userId, Long sessionId) {
        var rows = db.queryForList(
                "SELECT payload FROM wellbeing_record WHERE user_id=? AND kind=? AND record_key=? AND deleted=0",
                userId, KIND, String.valueOf(sessionId));
        if (rows.isEmpty()) {
            return null;
        }
        return decode(String.valueOf(rows.get(0).get("payload")));
    }

    private String encode(Object value) {
        try {
            return crypto.encrypt(json.writeValueAsString(value));
        } catch (Exception e) {
            throw new IllegalStateException("记录加密失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decode(String value) {
        try {
            return json.readValue(crypto.decrypt(value), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("记录读取失败", e);
        }
    }
}
