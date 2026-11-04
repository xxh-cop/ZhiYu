package com.zhiyu.modules.crisis;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.care.OperationLog;
import com.zhiyu.security.BusinessAccess;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 危机路径留存（AJ，2026-11-04 用户拍板：「留存但降级」）。
 *
 * <p><b>留存边界（只存这些，绝不含原文）</b>：user_id、关联会话/来源标识、场景、
 * 危机等级、规则式脱敏摘要（加密存储）、时间戳。不建任何原文列
 * （用户消息原文、AI 回复原文都不落本表）。
 *
 * <p><b>旁路异步</b>：记录走独立单线程 daemon executor，危机响应链路零 LLM、
 * 不被记录的加密/写库开销或失败阻塞/影响（失败仅记 warn）。
 *
 * <p><b>摘要生成</b>：规则式模板概括（命中词来自平台自有词表 + 场景 + 长度档），
 * 不经 LLM——危机路径本来就要求零 LLM 且要快，LLM 概括会引入延迟与降级复杂度；
 * 因此「LLM 不可用降级规则摘要」在本实现中天然成立（全程无 LLM）。
 * 摘要再过 {@link CrisisDesensitizer} 做防御性脱敏后加密落库。
 *
 * <p><b>可见性</b>：仅 role ∈ {2,3}；咨询师（role=2）只能看其认领关怀个案
 * （care_case.owner_id=本人）关联用户的记录（叠加既有授权关怀范围）；
 * 详情查看落 admin_operation_log（crisis_log.view）。学生端无任何下发入口。
 */
@Slf4j
@Service
public class CrisisSessionLogService {

    private final JdbcTemplate jdbc;
    private final BusinessAccess access;
    private final AesEncryptor crypto;
    private final OperationLog audit;
    private final ExecutorService worker;

    public CrisisSessionLogService(JdbcTemplate jdbc, BusinessAccess access,
                                   AesEncryptor crypto, OperationLog audit) {
        this.jdbc = jdbc;
        this.access = access;
        this.crypto = crypto;
        this.audit = audit;
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "crisis-log-writer");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    void shutdown() {
        worker.shutdown();
    }

    /**
     * 旁路记录一次危机命中（异步、失败吞掉）。由内容安全过滤器在输入 BLOCK 时调用。
     *
     * @param matchedKeyword 命中的平台词表关键词（非用户可识别信息）
     * @param textLength     输入长度（只取长度档，不取内容）
     * @param riskLevel      危机等级（AQ2：由词表分组得出，见 HighRiskLexicon.levelOf）
     */
    public void recordAsync(Long userId, Long sessionId, String scene,
                            String matchedKeyword, int textLength, int riskLevel) {
        if (userId == null) {
            return;
        }
        try {
            worker.execute(() -> recordSafely(userId, sessionId, scene, matchedKeyword, textLength, riskLevel));
        } catch (RuntimeException e) {
            log.warn("危机留存提交失败（不影响危机响应）: {}", e.getMessage());
        }
    }

    /** 同步写入（异步 worker 内执行；测试可直接调用）。 */
    void recordSafely(Long userId, Long sessionId, String scene, String matchedKeyword, int textLength,
                      int riskLevel) {
        try {
            String lengthBand = textLength <= 20 ? "短" : textLength <= 100 ? "中" : "长";
            String summary = "对话输入命中危机关键词「" + (matchedKeyword == null ? "高危词" : matchedKeyword)
                    + "」（场景 " + (scene == null ? "unknown" : scene) + "，输入长度" + lengthBand + "）";
            // 防御性脱敏（模板本不含用户原文，双保险）后应用层加密
            String cipher = crypto.encrypt(CrisisDesensitizer.sanitize(summary));
            jdbc.update("INSERT INTO crisis_session_log(id,user_id,session_id,scene,risk_level,summary_cipher,"
                            + "create_time,update_time,deleted) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",
                    IdWorker.getId(), userId, sessionId, scene, riskLevel, cipher);
        } catch (Exception e) {
            log.warn("危机留存写入失败（已消化，不影响危机响应）: {}", e.getMessage());
        }
    }

    // ── 授权查询（role ∈ {2,3}；咨询师限本人认领个案关联用户） ──

    /** 咨询师范围过滤 SQL 片段（管理员为空串）。 */
    private String scopeSql(Long actor) {
        Integer role = jdbc.queryForObject("SELECT role FROM user WHERE id=?", Integer.class, actor);
        if (role != null && role == 3) {
            return "";
        }
        return " AND l.user_id IN (SELECT r.user_id FROM care_reminder r"
                + " JOIN care_case c ON c.reminder_id=r.id AND c.deleted=0 AND c.owner_id=" + actor + ")";
    }

    /** 列表：只出元数据（不含摘要——摘要在详情且留痕）。AQ1：统一 camelCase。 */
    public Map<String, Object> list(int page) {
        Long actor = access.role(2, 3);
        if (page < 1 || page > 100000) {
            throw new BusinessException(1001, "页码超出范围");
        }
        String scope = scopeSql(actor);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crisis_session_log l WHERE l.deleted=0" + scope, Long.class);
        var rows = jdbc.queryForList(
                "SELECT l.id, l.user_id, l.session_id, l.scene, l.risk_level, l.create_time"
                        + " FROM crisis_session_log l WHERE l.deleted=0" + scope
                        + " ORDER BY l.create_time DESC, l.id DESC LIMIT 20 OFFSET " + ((page - 1) * 20));
        var records = rows.stream().map(row -> {
            Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
            m.put("id", String.valueOf(row.get("id")));
            m.put("userId", String.valueOf(row.get("user_id")));
            m.put("sessionId", row.get("session_id") == null ? null : String.valueOf(row.get("session_id")));
            m.put("scene", row.get("scene"));
            m.put("riskLevel", ((Number) row.get("risk_level")).intValue());
            m.put("createTime", String.valueOf(row.get("create_time")));
            return m;
        }).toList();
        return Map.of("records", records, "total", total == null ? 0 : total.intValue());
    }

    /**
     * 详情：解密返回脱敏摘要；每次查看落操作审计。越权/不存在 → 1004（不泄露存在性）。
     * AQ1：用户标识用 userId（雪花字符串）——关怀角色按个案归属定位用户已足够，
     * 昵称/姓名属多余暴露（最小权限），不下发。
     */
    public Map<String, Object> detail(Long id) {
        Long actor = access.role(2, 3);
        var rows = jdbc.queryForList(
                "SELECT l.id, l.user_id, l.session_id, l.scene, l.risk_level, l.summary_cipher, l.create_time"
                        + " FROM crisis_session_log l WHERE l.id=? AND l.deleted=0" + scopeSql(actor), id);
        if (rows.isEmpty()) {
            throw new BusinessException(1004, "记录不存在或无权查看");
        }
        var row = rows.get(0);
        String summary;
        try {
            summary = crypto.decrypt((String) row.get("summary_cipher"));
        } catch (Exception e) {
            summary = "（摘要无法读取）";
        }
        audit.record("crisis_log.view", id);
        return Map.of(
                "id", String.valueOf(row.get("id")),
                "userId", String.valueOf(row.get("user_id")),
                "sessionId", row.get("session_id") == null ? "" : String.valueOf(row.get("session_id")),
                "scene", row.get("scene"),
                "riskLevel", ((Number) row.get("risk_level")).intValue(),
                "summary", summary,
                "createTime", String.valueOf(row.get("create_time")));
    }
}
