package com.zhiyu.modules.admin;

import com.zhiyu.modules.admin.service.AdminWorkService;
import com.zhiyu.modules.admin.service.SoftTierPoolService;
import com.zhiyu.modules.file.MediaService;
import com.zhiyu.modules.notification.service.NotificationService;
import com.zhiyu.modules.work.service.CommentModerationService;
import com.zhiyu.modules.work.service.SoftTierRecordService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UPHOLD 裁决在两种驱动返回类型下的回归（2026-11-07，联调 F1-b）。
 *
 * <p>故障：UPHOLD 分支取台账 create_time 时按 {@link Timestamp} 强转，MySQL 下
 * ClassCastException → 裁决接口 500、UPHOLD 永不生效（真实 MySQL 实证）。
 * H2 返回 Timestamp，既有集成测试未暴露。本测试直接喂 MySQL 驱动形态（LocalDateTime）。
 */
class SoftTierPoolServiceVerdictDriverTypeTest {

    private record Capture(JdbcTemplate db, SoftTierPoolService svc) {
    }

    private Capture newService(Map<String, Object> reviewRow) {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForList(contains("FOR UPDATE"), any(Object.class))).thenReturn(List.of(reviewRow));
        SoftTierPoolService svc = new SoftTierPoolService(db,
                mock(NotificationService.class), mock(AdminWorkService.class),
                mock(CommentModerationService.class), mock(SoftTierRecordService.class),
                mock(MediaService.class));
        ReflectionTestUtils.setField(svc, "enabled", true);
        ReflectionTestUtils.setField(svc, "unsampledDemoteHours", 72);
        return new Capture(db, svc);
    }

    private Map<String, Object> reviewRow(Object createTime) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 900L);
        row.put("pool_status", "PENDING");
        row.put("target_type", "work");
        row.put("target_id", 123L);
        row.put("author_id", 4L);
        row.put("appeal_status", "NONE");
        row.put("create_time", createTime);
        return row;
    }

    @Test
    @DisplayName("MySQL 形态（create_time=LocalDateTime）：UPHOLD 生效，demote_until=create+72h")
    void upholdMysqlShape() {
        LocalDateTime created = LocalDateTime.of(2026, 11, 7, 12, 0, 0);
        Capture c = newService(reviewRow(created));
        c.svc().verdict(900L, 1L, "UPHOLD", "维持展示受限");

        // 台账更新与热列更新都应写入 create_time+72h（varargs 需逐参捕获）
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(c.db()).update(contains("SET pool_status=?"), captor.capture(), captor.capture(),
                captor.capture(), captor.capture(), captor.capture());
        Object demoteArg = captor.getAllValues().get(3);
        assertEquals(Timestamp.valueOf(created.plusHours(72)), demoteArg, "demote_until 应为 create_time+72h");

        ArgumentCaptor<Object> hot = ArgumentCaptor.forClass(Object.class);
        verify(c.db()).update(contains("UPDATE work SET soft_demote_until"), hot.capture(), hot.capture());
        assertEquals(Timestamp.valueOf(created.plusHours(72)), hot.getAllValues().get(0));
    }

    @Test
    @DisplayName("H2 形态（create_time=Timestamp）：UPHOLD 行为一致（不回归既有集成测试口径）")
    void upholdH2Shape() {
        LocalDateTime created = LocalDateTime.of(2026, 11, 7, 12, 0, 0);
        Capture c = newService(reviewRow(Timestamp.valueOf(created)));
        c.svc().verdict(900L, 1L, "UPHOLD", null);

        ArgumentCaptor<Object> hot = ArgumentCaptor.forClass(Object.class);
        verify(c.db()).update(contains("UPDATE work SET soft_demote_until"), hot.capture(), hot.capture());
        assertEquals(Timestamp.valueOf(created.plusHours(72)), hot.getAllValues().get(0));
    }
}
