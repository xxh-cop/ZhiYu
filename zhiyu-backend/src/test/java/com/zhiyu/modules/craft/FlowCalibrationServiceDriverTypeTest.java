package com.zhiyu.modules.craft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.modules.craft.calibration.FlowCalibrationProperties;
import com.zhiyu.modules.craft.calibration.FlowCalibrationService;
import com.zhiyu.modules.craft.mapper.CraftSessionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 心流自评校准聚合在两种驱动返回类型下的回归（2026-11-07，联调 F1 同类清扫）。
 *
 * <p>旧 {@code toLdt} 按 {@link Timestamp} 强转：craft_session.start_time/end_time
 * 在 MySQL（返回 {@link LocalDateTime}）下 ClassCastException → 管理端校准聚合 500。
 * 样本凑满 30 条才走得到统计分支，断言 status=ok 即证明转换全程无 CCE。
 */
class FlowCalibrationServiceDriverTypeTest {

    private static final int SAMPLES = 30;

    private FlowCalibrationService newService(Object startTime, Object endTime) {
        JdbcTemplate db = mock(JdbcTemplate.class);
        AesEncryptor crypto = mock(AesEncryptor.class);
        // payload 为加密 JSON：mock 解密出固定作答（score=6）
        when(crypto.decrypt(anyString())).thenReturn("{\"answered\":true,\"score\":6}");

        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < SAMPLES; i++) {
            Map<String, Object> r = new HashMap<>();
            r.put("record_key", String.valueOf(910000 + i));
            r.put("payload", "ENC");
            records.add(r);
        }
        when(db.queryForList(contains("FROM wellbeing_record WHERE kind=?"), any(Object.class), any(Object.class)))
                .thenReturn(records);

        Map<String, Object> session = new HashMap<>();
        session.put("flow_seconds", 300L);
        session.put("start_time", startTime);
        session.put("end_time", endTime);
        when(db.queryForList(contains("FROM craft_session WHERE id=?"), any(Object.class)))
                .thenReturn(List.of(session));

        return new FlowCalibrationService(db, crypto, new ObjectMapper(),
                mock(FlowCalibrationProperties.class), mock(CraftSessionMapper.class));
    }

    @Test
    @DisplayName("MySQL 形态（start/end=LocalDateTime）：30 样本聚合出结论，不抛 CCE")
    void mysqlShape() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 7, 12, 0, 0);
        LocalDateTime end = start.plusMinutes(10);
        Map<String, Object> out = newService(start, end).adminStats();
        assertEquals(SAMPLES, out.get("sampleSize"), "全部样本都应计入（时间列转换失败会 CCE，样本被静默跳过之外的更糟结局）");
        assertEquals("ok", out.get("status"));
    }

    @Test
    @DisplayName("H2 形态（Timestamp）：行为一致")
    void h2Shape() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 7, 12, 0, 0);
        LocalDateTime end = start.plusMinutes(10);
        Map<String, Object> out = newService(Timestamp.valueOf(start), Timestamp.valueOf(end)).adminStats();
        assertEquals(SAMPLES, out.get("sampleSize"));
        assertEquals("ok", out.get("status"));
    }
}
