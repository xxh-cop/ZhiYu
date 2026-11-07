package com.zhiyu.modules.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.modules.admin.service.FlowMetricsService;
import com.zhiyu.modules.craft.calibration.FlowCalibrationService;
import com.zhiyu.modules.craft.takedown.TakedownProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 心流看板聚合在两种驱动返回类型下的回归（2026-11-07，联调 F1 同类清扫）。
 *
 * <p>旧 {@code toLdt} 按 {@link Timestamp} 强转：会话行/事件聚合/项目下架时间任一列
 * 在 MySQL（返回 {@link LocalDateTime}）下都会 ClassCastException → 看板 500。
 * H2 返回 Timestamp，既有集成测试未暴露。本测试直接喂 MySQL 驱动形态。
 */
class FlowMetricsServiceDriverTypeTest {

    private FlowMetricsService newService(JdbcTemplate db) {
        FlowCalibrationService calibration = mock(FlowCalibrationService.class);
        when(calibration.adminStats()).thenReturn(Map.of("status", "insufficient"));
        return new FlowMetricsService(db, calibration, mock(TakedownProperties.class),
                mock(AesEncryptor.class), new ObjectMapper());
    }

    private Map<String, Object> sessionRow(Object start, Object end) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", 8801L);
        r.put("user_id", 4L);
        r.put("project_id", 9901L);
        r.put("status", 3);
        r.put("start_time", start);
        r.put("end_time", end);
        r.put("flow_seconds", 300L);
        return r;
    }

    private Map<String, Object> eventAggRow(Object lastEventAt) {
        Map<String, Object> r = new HashMap<>();
        r.put("session_id", 8801L);
        r.put("active_seconds", 600L);
        r.put("last_event_at", lastEventAt);
        r.put("first_archive_at", null);
        r.put("first_gate_blocked_at", null);
        return r;
    }

    private Map<String, Object> projectRow(Object takedownAt, Object archiveAt, Object exportUntil) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", 9901L);
        r.put("status", 1);
        r.put("takedown_kind", null);
        r.put("takedown_at", takedownAt);
        r.put("archive_at", archiveAt);
        r.put("export_until", exportUntil);
        r.put("level", 1);
        return r;
    }

    @SuppressWarnings("unchecked")
    private JdbcTemplate dbReturning(List<Map<String, Object>> sessions, List<Map<String, Object>> events,
                                     List<Map<String, Object>> projects) {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForList(contains("FROM craft_session WHERE deleted=0"), any(Object.class), any(Object.class)))
                .thenReturn((List) sessions);
        when(db.queryForList(contains("GROUP BY session_id"))).thenReturn((List) events);
        when(db.queryForList(contains("FROM craft_project WHERE id IN"))).thenReturn((List) projects);
        when(db.queryForList(contains("GROUP BY event_type"), any(Object.class), any(Object.class)))
                .thenReturn(List.of());
        when(db.queryForList(contains("FROM wellbeing_record"), any(Object.class), any(Object.class), any(Object.class)))
                .thenReturn(List.of());
        return db;
    }

    @Test
    @DisplayName("MySQL 形态（全部时间列 LocalDateTime）：看板聚合不抛 CCE 且纳入计数正确")
    void dashboardMysqlShape() {
        LocalDateTime start = LocalDateTime.now().minusHours(2);
        LocalDateTime end = LocalDateTime.now().minusHours(1);
        JdbcTemplate db = dbReturning(
                List.of(sessionRow(start, end)),
                List.of(eventAggRow(end)),
                List.of(projectRow(null, null, null)));
        Map<String, Object> out = newService(db).dashboard(null, null);
        Map<String, Object> totals = (Map<String, Object>) out.get("totals");
        assertEquals(1, totals.get("includedSessions"), "唯一已完成会话应被纳入（时间列转换成功才走得到这里）");
        assertEquals(1L, totals.get("activeUsers"));
    }

    @Test
    @DisplayName("H2 形态（Timestamp）：与 MySQL 形态行为一致")
    void dashboardH2Shape() {
        LocalDateTime start = LocalDateTime.now().minusHours(2);
        LocalDateTime end = LocalDateTime.now().minusHours(1);
        JdbcTemplate db = dbReturning(
                List.of(sessionRow(Timestamp.valueOf(start), Timestamp.valueOf(end))),
                List.of(eventAggRow(Timestamp.valueOf(end))),
                List.of(projectRow(null, null, null)));
        Map<String, Object> out = newService(db).dashboard(null, null);
        Map<String, Object> totals = (Map<String, Object>) out.get("totals");
        assertEquals(1, totals.get("includedSessions"));
    }
}
