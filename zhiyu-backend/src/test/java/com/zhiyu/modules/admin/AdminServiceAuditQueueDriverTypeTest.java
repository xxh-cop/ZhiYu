package com.zhiyu.modules.admin;

import com.zhiyu.modules.admin.service.impl.AdminServiceImpl;
import com.zhiyu.modules.admin.dto.AuditQueueItemVO;
import com.zhiyu.modules.audit.mapper.CareReminderMapper;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.care.OperationLog;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.craft.takedown.TakedownProperties;
import com.zhiyu.modules.emotion.mapper.EmotionSessionMapper;
import com.zhiyu.modules.file.MediaService;
import com.zhiyu.modules.notification.service.NotificationService;
import com.zhiyu.modules.work.mapper.WorkMapper;
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
 * 审核队列 create_time 在两种驱动返回类型下的回归（2026-11-07，联调 F1 同类清扫）。
 *
 * <p>旧 {@code toLdt} 按 {@link Timestamp} 强转：审核队列（work/comment）create_time 列
 * 在 MySQL（返回 {@link LocalDateTime}）下 ClassCastException → 队列接口 500。
 */
class AdminServiceAuditQueueDriverTypeTest {

    private AdminServiceImpl newService(JdbcTemplate jdbc) {
        return new AdminServiceImpl(
                mock(CraftProjectMapper.class), mock(CareReminderMapper.class),
                mock(UserMapper.class), mock(WorkMapper.class), mock(EmotionSessionMapper.class),
                jdbc, mock(NotificationService.class), mock(OperationLog.class),
                mock(MediaService.class), mock(TakedownProperties.class));
    }

    @SuppressWarnings("unchecked")
    private JdbcTemplate dbReturning(Object createTime) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("COUNT(*) FROM work WHERE status=0"), eq(Long.class))).thenReturn(1L);
        Map<String, Object> row = new HashMap<>();
        row.put("id", 8801L);
        row.put("title", "待审作品");
        row.put("user_id", 4L);
        row.put("media_type", "text");
        row.put("create_time", createTime);
        when(jdbc.queryForList(contains("FROM work WHERE status=0"), any(Object.class), any(Object.class)))
                .thenReturn((List) List.of(row));
        when(jdbc.queryForList(contains("FROM ai_audit_log"), any(Object.class), any(Object.class)))
                .thenReturn(List.of());
        return jdbc;
    }

    @Test
    @DisplayName("MySQL 形态（create_time=LocalDateTime）：队列正常返回，时间不丢")
    void mysqlShape() {
        LocalDateTime created = LocalDateTime.of(2026, 11, 7, 12, 0, 0);
        var page = newService(dbReturning(created)).getAuditQueue("work", 1);
        assertEquals(1, page.getRecords().size());
        AuditQueueItemVO vo = page.getRecords().get(0);
        assertEquals(created, vo.getCreateTime(), "MySQL 形态 create_time 必须保真（旧强转在此 CCE）");
    }

    @Test
    @DisplayName("H2 形态（Timestamp）：行为一致")
    void h2Shape() {
        LocalDateTime created = LocalDateTime.of(2026, 11, 7, 12, 0, 0);
        var page = newService(dbReturning(Timestamp.valueOf(created))).getAuditQueue("work", 1);
        assertEquals(created, page.getRecords().get(0).getCreateTime());
    }
}
