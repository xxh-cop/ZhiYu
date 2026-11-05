package com.zhiyu.modules.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.admin.service.FlowMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 心流/专注度量看板聚合（阶段 1-2，2026-11-05）测试。
 *
 * <p>覆盖：区间校验（1001）、只读/归档/待审剔除（含事件流 session_archive 校正）、
 * 分母分子同口径（step_leave 之和 vs flow_seconds）、无事件会话不进分布、
 * insufficient 传参与 unavailable 清单、自评响应率（区分没看到/看到不答）、
 * operational 分面不并线、level/时长桶分层与 sparse 标记、人×周中位数。
 * H2 下 occurred_at 走 Timestamp 分支（toLdt 口径），MySQL 的 LocalDateTime 分支由冒烟覆盖。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FlowMetricsTest {

    @Autowired
    private FlowMetricsService service;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AesEncryptor crypto;
    @Autowired
    private ObjectMapper json;

    private static final long STUDENT = 960003L;
    private static final long INHERITOR = 960002L;
    private static final long P_NORMAL = 960100L;
    private static final long P_COMPLIANCE = 960101L;
    private static final long P_ARCHIVED = 960102L;
    private static final long P_PENDING = 960103L;
    private static final long P_OPERATIONAL = 960104L;

    private final LocalDateTime now = LocalDateTime.now();

    @BeforeEach
    void setup() {
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?, 'fm_stu', 'unused', '学生',0,1,1,0,0)", STUDENT);
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?, 'fm_inh', 'unused', '传承人',1,1,1,0,0)", INHERITOR);
    }

    // ── 造数工具 ──

    private void project(long id, int status, String kind,
                         LocalDateTime takedownAt, LocalDateTime archiveAt, LocalDateTime exportUntil) {
        project(id, status, kind, takedownAt, archiveAt, exportUntil, 1);
    }

    private void project(long id, int status, String kind,
                         LocalDateTime takedownAt, LocalDateTime archiveAt, LocalDateTime exportUntil, int level) {
        jdbc.update("INSERT INTO craft_project(id,inheritor_id,title,level,category,status,"
                        + "takedown_kind,takedown_at,archive_at,export_until,create_time,update_time,deleted)"
                        + " VALUES(?,?,?,?,'扎染',?,?,?,?,?,NOW(),NOW(),0)",
                id, INHERITOR, "看板测试项目" + id, level, status, kind,
                takedownAt == null ? null : Timestamp.valueOf(takedownAt),
                archiveAt == null ? null : Timestamp.valueOf(archiveAt),
                exportUntil == null ? null : Timestamp.valueOf(exportUntil));
    }

    private void session(long id, long projectId, int status,
                         LocalDateTime start, LocalDateTime end, int flowSeconds) {
        jdbc.update("INSERT INTO craft_session(id,user_id,project_id,status,current_step,"
                        + "start_time,end_time,flow_seconds,create_time,update_time,deleted)"
                        + " VALUES(?,?,?,?,1,?,?,?,NOW(),NOW(),0)",
                id, STUDENT, projectId, status,
                Timestamp.valueOf(start), end == null ? null : Timestamp.valueOf(end), flowSeconds);
    }

    private void stepLeave(long eventId, long sessionId, long projectId, int stepNo,
                           LocalDateTime occurredAt, long clientSeq, int dwellSeconds) {
        jdbc.update("INSERT INTO craft_session_event(id,session_id,user_id,craft_project_id,event_type,step_no,"
                        + "occurred_at,client_seq,context,dwell_seconds,create_time,update_time,deleted)"
                        + " VALUES(?,?,?,?,'step_leave',?,?,?,NULL,?,NOW(),NOW(),0)",
                eventId, sessionId, STUDENT, projectId, stepNo, Timestamp.valueOf(occurredAt),
                clientSeq, dwellSeconds);
    }

    private void archiveEvent(long eventId, long sessionId, long projectId, LocalDateTime occurredAt) {
        jdbc.update("INSERT INTO craft_session_event(id,session_id,user_id,craft_project_id,event_type,step_no,"
                        + "occurred_at,client_seq,context,dwell_seconds,create_time,update_time,deleted)"
                        + " VALUES(?,?,?,?,'session_archive',NULL,?,NULL,NULL,NULL,NOW(),NOW(),0)",
                eventId, sessionId, STUDENT, projectId, Timestamp.valueOf(occurredAt));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call() {
        return service.dashboard(null, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> totals(Map<String, Object> out) {
        return (Map<String, Object>) out.get("totals");
    }

    // ── 区间校验 ──

    @Test
    @DisplayName("区间校验：from>to / 跨度>90 天 / 非法格式 → 1001")
    void rangeValidation() {
        BusinessException e1 = assertThrows(BusinessException.class,
                () -> service.dashboard("2026-11-05", "2026-11-01"));
        assertEquals(1001, e1.getCode());
        BusinessException e2 = assertThrows(BusinessException.class,
                () -> service.dashboard("2026-08-01", "2026-11-05"));
        assertEquals(1001, e2.getCode());
        BusinessException e3 = assertThrows(BusinessException.class,
                () -> service.dashboard("2026/11/01", null));
        assertEquals(1001, e3.getCode());
        // 恰好 90 天合法
        assertDoesNotThrow(() -> service.dashboard("2026-08-08", "2026-11-05"));
    }

    // ── 剔除规则 ──

    @Test
    @DisplayName("剔除：合规下架只读/归档/待审会话剔除并透明返回；下架前完成的会话保留")
    void exclusionRules() {
        project(P_NORMAL, 1, null, null, null, null);
        // 合规下架：昨天下架，窗口 72h 未过 → 当前只读
        project(P_COMPLIANCE, 2, "compliance", now.minusDays(1), null, now.plusDays(2));
        project(P_ARCHIVED, 3, "operational", now.minusDays(20), now.minusDays(1), null);
        project(P_PENDING, 0, null, null, null, null);

        // A：正常会话（完成，活跃 600s，flow 300s）→ 纳入
        session(960200L, P_NORMAL, 3, now.minusDays(2), now.minusDays(2).plusSeconds(700), 300);
        stepLeave(960300L, 960200L, P_NORMAL, 1, now.minusDays(2).plusSeconds(600), 1, 600);
        // B：合规下架项目，下架后仍有活动（end 晚于 takedown_at）→ 剔除 read_only
        session(960201L, P_COMPLIANCE, 3, now.minusDays(2), now.minusHours(2), 100);
        stepLeave(960301L, 960201L, P_COMPLIANCE, 1, now.minusHours(3), 1, 500);
        // C：合规下架项目，但下架前已完成 → 保留
        session(960202L, P_COMPLIANCE, 3, now.minusDays(3), now.minusDays(3).plusSeconds(400), 50);
        stepLeave(960302L, 960202L, P_COMPLIANCE, 1, now.minusDays(3).plusSeconds(400), 1, 400);
        // D：归档项目，归档后仍有活动 → 剔除 read_only
        session(960203L, P_ARCHIVED, 3, now.minusDays(2), now.minusHours(1), 80);
        stepLeave(960303L, 960203L, P_ARCHIVED, 1, now.minusHours(2), 1, 300);
        // E：待审项目会话 → 剔除 pending_project
        session(960204L, P_PENDING, 3, now.minusDays(2), now.minusDays(2).plusSeconds(300), 10);
        stepLeave(960304L, 960204L, P_PENDING, 1, now.minusDays(2).plusSeconds(300), 1, 300);

        Map<String, Object> out = call();
        Map<String, Object> totals = totals(out);
        assertEquals(5, totals.get("sessionsInRange"));
        assertEquals(2, totals.get("excludedReadOnly"));
        assertEquals(1, totals.get("excludedPendingProject"));
        assertEquals(0, totals.get("excludedNoEvents"));
        assertEquals(2, totals.get("includedSessions"));
        // 分母分子同口径：只剩 A(600s/300) + C(400s/50)
        assertEquals(1000L, totals.get("activeSeconds"));
        assertEquals(350L, totals.get("flowSeconds"));

        List<Map<String, Object>> exclusions = (List<Map<String, Object>>) out.get("exclusions");
        assertEquals(3, exclusions.size());
        assertTrue(exclusions.stream().anyMatch(x -> "read_only".equals(x.get("reason"))
                && "960201".equals(x.get("sessionId")) && x.get("readOnlySince") != null));
        assertTrue(exclusions.stream().anyMatch(x -> "pending_project".equals(x.get("reason"))
                && "960204".equals(x.get("sessionId"))));
    }

    @Test
    @DisplayName("剔除：事件流 session_archive 首次时点可作为只读起点证据（项目当前上架也剔）")
    void archiveEventAsReadOnlyEvidence() {
        project(P_NORMAL, 1, null, null, null, null);
        // 项目当前上架（resolver 非只读），但事件流有 session_archive → 以事件时点为只读起点
        session(960210L, P_NORMAL, 3, now.minusDays(1), now, 100);
        stepLeave(960310L, 960210L, P_NORMAL, 1, now.minusMinutes(30), 1, 500);
        archiveEvent(960311L, 960210L, P_NORMAL, now.minusHours(1));

        Map<String, Object> out = call();
        assertEquals(1, totals(out).get("excludedReadOnly"));
        assertEquals(0, totals(out).get("includedSessions"));
    }

    // ── 分母分子同口径 / 分布 ──

    @Test
    @DisplayName("同口径：分母=step_leave 之和，占比=flow/active；无事件会话不进分布；人×周中位数")
    void sameRulerAndDistribution() {
        project(P_NORMAL, 1, null, null, null, null);
        // 会话一：两条 step_leave 600+400=1000s，flow 250 → ratio 0.25
        session(960220L, P_NORMAL, 3, now.minusDays(2), now.minusDays(2).plusSeconds(1100), 250);
        stepLeave(960320L, 960220L, P_NORMAL, 1, now.minusDays(2).plusSeconds(600), 1, 600);
        stepLeave(960321L, 960220L, P_NORMAL, 2, now.minusDays(2).plusSeconds(1000), 2, 400);
        // 会话二：同一人同一周，活跃 500s，flow 50 → ratio 0.1；人×周中位数 = (0.25+0.1)/2 = 0.175
        session(960221L, P_NORMAL, 3, now.minusDays(1), now.minusDays(1).plusSeconds(520), 50);
        stepLeave(960322L, 960221L, P_NORMAL, 1, now.minusDays(1).plusSeconds(500), 1, 500);
        // 会话三：完成但无事件 → excludedNoEvents，不进分布
        session(960222L, P_NORMAL, 3, now.minusDays(1), now.minusDays(1).plusSeconds(300), 30);
        // 会话四：进行中 → inProgress，不进分布
        session(960223L, P_NORMAL, 1, now.minusHours(1), null, 0);

        Map<String, Object> out = call();
        Map<String, Object> totals = totals(out);
        assertEquals(4, totals.get("sessionsInRange"));
        assertEquals(1, totals.get("inProgressSessions"));
        assertEquals(1, totals.get("excludedNoEvents"));
        assertEquals(2, totals.get("includedSessions"));
        assertEquals(1500L, totals.get("activeSeconds"));
        assertEquals(300L, totals.get("flowSeconds"));

        Map<String, Object> distribution = (Map<String, Object>) out.get("distribution");
        assertEquals(1, distribution.get("personWeeks"));
        assertEquals(0.175, (Double) distribution.get("p50"), 1e-9);
        List<Map<String, Object>> buckets = (List<Map<String, Object>>) distribution.get("buckets");
        assertEquals(10, buckets.size());
        // 0.175 落 [0.1,0.2) 桶
        assertEquals(1, buckets.get(1).get("count"));

        List<Map<String, Object>> weekly = (List<Map<String, Object>>) out.get("weekly");
        assertEquals(1, weekly.size());
        assertEquals(0.175, (Double) weekly.get(0).get("medianRatio"), 1e-9);
        assertEquals(1L, weekly.get(0).get("activeUsers"));
        assertEquals(2, weekly.get(0).get("sessionCount"));
    }

    // ── insufficient / 自评响应率 / unavailable ──

    @Test
    @DisplayName("insufficient：无作答样本 → metricLabel=专注停留占比；unavailable 含自评相关系数")
    void insufficientPropagates() {
        project(P_NORMAL, 1, null, null, null, null);
        session(960230L, P_NORMAL, 3, now.minusDays(1), now.minusDays(1).plusSeconds(400), 100);
        stepLeave(960330L, 960230L, P_NORMAL, 1, now.minusDays(1).plusSeconds(400), 1, 400);

        Map<String, Object> out = call();
        assertEquals("专注停留占比", out.get("metricLabel"));
        Map<String, Object> calibration = (Map<String, Object>) out.get("calibration");
        assertEquals("insufficient", calibration.get("status"));
        List<Map<String, Object>> unavailable = (List<Map<String, Object>>) out.get("unavailable");
        assertTrue(unavailable.stream().anyMatch(u -> "self_report_rho".equals(u.get("key"))));
        // 结构性不可得项恒在
        assertTrue(unavailable.stream().anyMatch(u -> "export_success_rate".equals(u.get("key"))));
        assertTrue(unavailable.stream().anyMatch(u -> "notification_receipt".equals(u.get("key"))));
        // 空区间方向：0 作答时 responseRate 不为 null 的断言见下一条
    }

    @Test
    @DisplayName("自评响应率：区分「没看到」（noResponse）与「看到不答」（skipped）")
    void selfReportRates() throws Exception {
        writeFlowCal(960240L, true, true, false, 6);   // 抽中且作答
        writeFlowCal(960241L, true, false, true, null); // 抽中但跳过（看到不答）
        writeFlowCal(960242L, true, false, false, null);// 抽中无响应（没看到）
        writeFlowCal(960243L, false, false, false, null);// 未抽中（不计入 sampled）

        Map<String, Object> out = call();
        Map<String, Object> selfReport = (Map<String, Object>) out.get("selfReport");
        assertEquals(3, selfReport.get("sampled"));
        assertEquals(1, selfReport.get("answered"));
        assertEquals(1, selfReport.get("skipped"));
        assertEquals(1, selfReport.get("noResponse"));
        assertEquals(1.0 / 3.0, (Double) selfReport.get("responseRate"), 1e-9);
    }

    private void writeFlowCal(long sessionId, boolean sampled, boolean answered, boolean skipped, Integer score)
            throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sampled", sampled);
        payload.put("sampledAt", now.minusDays(1).toString());
        payload.put("answered", answered);
        payload.put("skipped", skipped);
        payload.put("score", score);
        jdbc.update("INSERT INTO wellbeing_record(id,user_id,kind,record_key,payload,create_time,update_time,deleted)"
                        + " VALUES(?,?,'FLOW_CAL',?,?,NOW(),NOW(),0)",
                960500L + sessionId % 100, STUDENT, String.valueOf(sessionId),
                crypto.encrypt(json.writeValueAsString(payload)));
    }

    // ── 分层：operational 不并线 / level / 时长桶 / sparse ──

    @Test
    @DisplayName("分面：运营下架未到期会话计入但单独成面，不与常态并线；level 与时长桶分层；users<10 标 sparse")
    void facetsSeparated() {
        project(P_NORMAL, 1, null, null, null, null, 1);
        // 运营下架未到期（archive_at 未来）→ 不计只读，会话纳入但分面 operational
        project(P_OPERATIONAL, 2, "operational", now.minusDays(1), now.plusDays(10), null, 2);

        session(960250L, P_NORMAL, 3, now.minusDays(2), now.minusDays(2).plusSeconds(700), 210);
        stepLeave(960350L, 960250L, P_NORMAL, 1, now.minusDays(2).plusSeconds(600), 1, 600); // ratio 0.35, m5to15
        session(960251L, P_OPERATIONAL, 3, now.minusDays(1), now.minusDays(1).plusSeconds(200), 0);
        stepLeave(960351L, 960251L, P_OPERATIONAL, 1, now.minusDays(1).plusSeconds(200), 1, 200); // ratio 0, lt5

        Map<String, Object> out = call();
        assertEquals(2, totals(out).get("includedSessions"));

        Map<String, Object> facets = (Map<String, Object>) out.get("facets");
        List<Map<String, Object>> byKind = (List<Map<String, Object>>) facets.get("byTakedownKind");
        assertEquals(2, byKind.size());
        Map<String, Object> none = byKind.stream().filter(c -> "none".equals(c.get("key"))).findFirst().orElseThrow();
        Map<String, Object> op = byKind.stream().filter(c -> "operational".equals(c.get("key"))).findFirst().orElseThrow();
        assertEquals(1, none.get("sessions"));
        assertEquals(1, op.get("sessions"));
        assertEquals(0.35, (Double) none.get("p50"), 1e-9);
        assertEquals(0.0, (Double) op.get("p50"), 1e-9);
        // 单元用户数 1 < 10 → sparse
        assertEquals(true, none.get("sparse"));
        assertEquals(true, op.get("sparse"));

        List<Map<String, Object>> byLevel = (List<Map<String, Object>>) facets.get("byLevel");
        assertEquals(2, byLevel.size());
        assertTrue(byLevel.stream().anyMatch(c -> "1".equals(c.get("key"))));
        assertTrue(byLevel.stream().anyMatch(c -> "2".equals(c.get("key"))));

        List<Map<String, Object>> byBucket = (List<Map<String, Object>>) facets.get("byDurationBucket");
        assertTrue(byBucket.stream().anyMatch(c -> "m5to15".equals(c.get("key"))));
        assertTrue(byBucket.stream().anyMatch(c -> "lt5".equals(c.get("key"))));
    }

    @Test
    @DisplayName("事件信号计数：区间内全量按类型返回；无数据的类型为 0")
    void eventSignals() {
        project(P_NORMAL, 1, null, null, null, null);
        session(960260L, P_NORMAL, 3, now.minusDays(1), now.minusDays(1).plusSeconds(400), 100);
        stepLeave(960360L, 960260L, P_NORMAL, 1, now.minusDays(1).plusSeconds(400), 1, 400);

        Map<String, Object> out = call();
        Map<String, Integer> events = (Map<String, Integer>) out.get("events");
        assertEquals(1, events.get("step_leave"));
        assertEquals(0, events.get("breathing_prompt"));
        assertEquals(0, events.get("suspected_away"));
        // 口径变更三条固定下发
        List<Map<String, Object>> changes = (List<Map<String, Object>>) out.get("definitionChanges");
        assertEquals(3, changes.size());
        assertTrue(changes.stream().allMatch(c -> "2026-11-05".equals(c.get("date"))));
    }
}
