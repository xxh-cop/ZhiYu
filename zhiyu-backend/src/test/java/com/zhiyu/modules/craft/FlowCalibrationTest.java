package com.zhiyu.modules.craft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.craft.calibration.FlowCalibrationProperties;
import com.zhiyu.modules.craft.calibration.FlowCalibrationService;
import com.zhiyu.modules.craft.controller.CraftController;
import com.zhiyu.modules.craft.service.CraftSessionService;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 心流自评校准（阶段 1-1，2026-11-05）闭环测试。
 *
 * <p>覆盖：抽样落库与稳定性（rate=1 必中 / rate=0 必不中 / 重复 finish 不覆盖）、
 * 跳过落记录、跳过可补答、重复作答 3001、评分越界 1001、非本人 1003、未登录 1002、
 * 会话不存在 1004、管理端 insufficient、Spearman ρ 阈值两个方向的指标名判定。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FlowCalibrationTest {

    @Autowired
    private FlowCalibrationService calibrationService;
    @Autowired
    private FlowCalibrationProperties calibrationProperties;
    @Autowired
    private CraftSessionService craftSessionService;
    @Autowired
    private CraftController craftController;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AesEncryptor crypto;
    @Autowired
    private ObjectMapper json;

    private static final long STUDENT = 980003L;
    private static final long OTHER = 980004L;
    private static final long INHERITOR = 980002L;
    private static final long PROJECT = 980100L;

    private double oldRate;
    private boolean oldEnabled;

    @BeforeEach
    void setup() {
        oldRate = calibrationProperties.getSampleRate();
        oldEnabled = calibrationProperties.isEnabled();
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?,?, 'unused',?,?,1,1,0,0)", STUDENT, "cal_stu", "学生", 0);
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?,?, 'unused',?,?,1,1,0,0)", OTHER, "cal_other", "学生", 0);
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?,?, 'unused',?,?,1,1,0,0)", INHERITOR, "cal_inh", "传承人", 1);
        jdbc.update("INSERT INTO craft_project(id,inheritor_id,title,level,category,status,create_time,update_time,deleted)"
                + " VALUES(?,?,?,1,'扎染',1,NOW(),NOW(),0)", PROJECT, INHERITOR, "校准测试项目");
        jdbc.update("INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted)"
                + " VALUES(980300,?,1,'步骤一','引导',NOW(),NOW(),0)", PROJECT);
        UserContext.set(new LoginUser(STUDENT, 0));
    }

    @AfterEach
    void clear() {
        calibrationProperties.setSampleRate(oldRate);
        calibrationProperties.setEnabled(oldEnabled);
        UserContext.clear();
    }

    /** 创建一个会话并 finish（触发抽样钩子），返回 sessionId。 */
    private Long finishSession(double rate) {
        calibrationProperties.setSampleRate(rate);
        Long sid = craftSessionService.create(STUDENT, PROJECT, null).getSessionId();
        craftSessionService.changeStatus(STUDENT, sid, "finish");
        return sid;
    }

    @Test
    @DisplayName("抽样落库稳定：rate=1 必中且重复查询同一结论；重复 finish 不覆盖；rate=0 必不中")
    void samplingPersistedAndStable() {
        Long sid = finishSession(1.0);
        var first = calibrationService.query(STUDENT, sid);
        var second = calibrationService.query(STUDENT, sid);
        assertTrue((Boolean) first.get("sampled"));
        assertTrue((Boolean) second.get("sampled"), "同一会话重复询问同一结论");
        assertEquals(first.get("sampled"), second.get("sampled"));
        assertNotNull(first.get("question"));
        @SuppressWarnings("unchecked")
        Map<String, Object> scale = (Map<String, Object>) first.get("scale");
        assertEquals(1, scale.get("min"));
        assertEquals(7, scale.get("max"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM wellbeing_record WHERE user_id=? AND kind='FLOW_CAL' AND record_key=? AND deleted=0",
                Integer.class, STUDENT, String.valueOf(sid)), "抽样结论落库唯一一行");

        // 幂等：重复触发 finish 钩子不覆盖已有记录
        jdbc.queryForObject("SELECT payload FROM wellbeing_record WHERE user_id=? AND kind='FLOW_CAL' AND record_key=?",
                String.class, STUDENT, String.valueOf(sid));
        var session = new com.zhiyu.modules.craft.entity.CraftSession();
        session.setId(sid);
        session.setUserId(STUDENT);
        calibrationService.onSessionFinished(session);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM wellbeing_record WHERE user_id=? AND kind='FLOW_CAL' AND record_key=? AND deleted=0",
                Integer.class, STUDENT, String.valueOf(sid)));

        // rate=0 必不中
        Long never = finishSession(0.0);
        assertFalse((Boolean) calibrationService.query(STUDENT, never).get("sampled"));

        // enabled=false 不产生记录
        calibrationProperties.setEnabled(false);
        Long off = craftSessionService.create(STUDENT, PROJECT, null).getSessionId();
        craftSessionService.changeStatus(STUDENT, off, "finish");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM wellbeing_record WHERE user_id=? AND kind='FLOW_CAL' AND record_key=? AND deleted=0",
                Integer.class, STUDENT, String.valueOf(off)));
    }

    @Test
    @DisplayName("提交与跳过：跳过落记录、跳后可补答、重复作答 3001、越界/空 1001、未抽中 3001")
    void submitAndSkipFlow() {
        Long sid = finishSession(1.0);

        // 跳过：落「已跳过」记录（区分没看到 vs 看了不答）
        var skipped = calibrationService.submit(STUDENT, sid, null, true);
        assertTrue((Boolean) skipped.get("skipped"));
        assertFalse((Boolean) skipped.get("answered"));
        assertTrue((Boolean) skipped.get("sampled"), "跳过不改变抽中事实");

        // 评分越界
        assertEquals(1001, assertThrows(BusinessException.class,
                () -> calibrationService.submit(STUDENT, sid, 0, false)).getCode());
        assertEquals(1001, assertThrows(BusinessException.class,
                () -> calibrationService.submit(STUDENT, sid, 8, false)).getCode());
        // 两字段都空
        assertEquals(1001, assertThrows(BusinessException.class,
                () -> calibrationService.submit(STUDENT, sid, null, false)).getCode());

        // 跳过后补答允许（用户改变主意）
        var answered = calibrationService.submit(STUDENT, sid, 6, false);
        assertTrue((Boolean) answered.get("answered"));
        assertEquals(6, answered.get("score"));
        // 已作答重复提交 → 3001
        assertEquals(3001, assertThrows(BusinessException.class,
                () -> calibrationService.submit(STUDENT, sid, 5, false)).getCode(), "已作答不可重复提交");

        // 未抽中（rate=0）→ 3001
        Long never = finishSession(0.0);
        assertEquals(3001, assertThrows(BusinessException.class,
                () -> calibrationService.submit(STUDENT, never, 5, false)).getCode(), "未抽中不可提交");
    }

    @Test
    @DisplayName("权限：非本人 1003；会话不存在 1004；未登录 1002（查询/提交同口径）")
    void ownershipAndAuth() {
        Long sid = finishSession(1.0);
        assertEquals(1003, assertThrows(BusinessException.class,
                () -> calibrationService.query(OTHER, sid)).getCode(), "非本人查询 1003");
        assertEquals(1003, assertThrows(BusinessException.class,
                () -> calibrationService.submit(OTHER, sid, 5, false)).getCode(), "非本人提交 1003");
        assertEquals(1004, assertThrows(BusinessException.class,
                () -> calibrationService.query(STUDENT, 989999L)).getCode(), "会话不存在 1004");

        UserContext.clear();
        assertEquals(1002, assertThrows(BusinessException.class,
                () -> craftController.getCalibration(sid)).getCode(), "未登录查询 1002");
        var dto = new com.zhiyu.modules.craft.dto.CalibrationSubmitDTO();
        dto.setScore(5);
        assertEquals(1002, assertThrows(BusinessException.class,
                () -> craftController.submitCalibration(sid, dto)).getCode(), "未登录提交 1002");
    }

    @Test
    @DisplayName("管理端聚合：样本不足 → insufficient + rho=null + 保守指标名")
    void adminStatsInsufficient() {
        var stats = calibrationService.adminStats();
        assertEquals("insufficient", stats.get("status"));
        assertNull(stats.get("rho"));
        assertEquals("专注停留占比", stats.get("metricLabel"), "无效度证据时保守命名");
        assertEquals(0, ((Number) stats.get("sampleSize")).intValue());
    }

    @Test
    @DisplayName("管理端聚合：ρ≥0.3 →「心流占时」；ρ<0.3 → 降级「专注停留占比」（Spearman 双方向）")
    void adminStatsThresholdBothDirections() throws Exception {
        // 30 个已作答样本：自评单调升 + 占比单调升 → ρ≈1
        fabricateSamples(982000L, true);
        var positive = calibrationService.adminStats();
        assertEquals("ok", positive.get("status"));
        assertEquals(30, ((Number) positive.get("sampleSize")).intValue());
        assertTrue(((Number) positive.get("rho")).doubleValue() >= 0.3, "正相关 ρ: " + positive.get("rho"));
        assertEquals("心流占时", positive.get("metricLabel"));
        assertEquals("spearman", positive.get("method"));
    }

    @Test
    @DisplayName("管理端聚合：自评与占比反向（ρ≈-1）→ 降级「专注停留占比」")
    void adminStatsNegativeDirection() throws Exception {
        fabricateSamples(983000L, false);
        var negative = calibrationService.adminStats();
        assertEquals("ok", negative.get("status"));
        assertTrue(((Number) negative.get("rho")).doubleValue() < 0.3, "反向 ρ: " + negative.get("rho"));
        assertEquals("专注停留占比", negative.get("metricLabel"), "ρ<0.3 必须降级");
    }

    /**
     * 造 30 条「已作答」校准记录 + 对应已完成会话。
     *
     * @param positive true=自评与占比同向（ρ≈1）；false=反向（ρ≈-1）
     */
    private void fabricateSamples(long base, boolean positive) throws Exception {
        for (int i = 0; i < 30; i++) {
            long sid = base + i;
            int score = 1 + (int) Math.round(i * 6.0 / 29); // 1..7 单调不减
            double ratio = positive ? (i + 1) * 0.01 : (30 - i) * 0.01; // 与自评同向/反向
            int duration = 1000;
            int flow = (int) Math.round(ratio * duration);
            jdbc.update("INSERT INTO craft_session(id,user_id,project_id,status,current_step,start_time,end_time,"
                            + "flow_seconds,create_time,update_time,deleted) VALUES(?,?,?,3,1,?,?,?,NOW(),NOW(),0)",
                    sid, STUDENT, PROJECT,
                    Timestamp.valueOf(LocalDateTime.now().minusSeconds(duration)),
                    Timestamp.valueOf(LocalDateTime.now()), flow);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sampled", true);
            payload.put("sampledAt", LocalDateTime.now().toString());
            payload.put("answered", true);
            payload.put("skipped", false);
            payload.put("score", score);
            payload.put("answeredAt", LocalDateTime.now().toString());
            jdbc.update("INSERT INTO wellbeing_record(id,user_id,kind,record_key,payload,create_time,update_time,deleted)"
                            + " VALUES(?,?,?,?,?,NOW(),NOW(),0)",
                    com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(), STUDENT, "FLOW_CAL",
                    String.valueOf(sid), crypto.encrypt(json.writeValueAsString(payload)));
        }
    }
}
