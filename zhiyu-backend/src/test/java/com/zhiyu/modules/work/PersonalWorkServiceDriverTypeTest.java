package com.zhiyu.modules.work;

import com.zhiyu.modules.file.MediaService;
import com.zhiyu.modules.work.service.PersonalWorkService;
import com.zhiyu.security.BusinessAccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

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
 * 「我的作品」软档字段在两种驱动返回类型下的回归（2026-11-07，联调 F1-a）。
 *
 * <p>故障：MySQL Connector/J 对 DATETIME 返回 {@link LocalDateTime}，旧代码按
 * {@link Timestamp} 强转 → ClassCastException → GET /api/works/mine 500（真实 MySQL 实证）。
 * H2 返回 Timestamp，故既有集成测试全绿未暴露。本测试用 mock 的 JdbcTemplate 直接喂
 * 两种驱动形态，不再依赖 H2 的返回类型「碰巧」一致。
 */
class PersonalWorkServiceDriverTypeTest {

    private PersonalWorkService newService(List<Map<String, Object>> rows) {
        JdbcTemplate db = mock(JdbcTemplate.class);
        BusinessAccess access = mock(BusinessAccess.class);
        MediaService media = mock(MediaService.class);
        when(access.user()).thenReturn(4L);
        when(db.queryForObject(contains("COUNT(*) FROM work"), eq(Long.class), any(Object[].class)))
                .thenReturn((long) rows.size());
        when(db.queryForList(contains("softDemoteUntil"), any(Object[].class))).thenReturn(rows);
        when(db.queryForList(contains("GROUP BY status"), any(Object.class))).thenReturn(List.of());
        when(media.imageUrl(anyLong(), any())).thenAnswer(inv -> inv.getArgument(1));
        PersonalWorkService svc = new PersonalWorkService(media, db, access);
        ReflectionTestUtils.setField(svc, "softDispositionEnabled", true);
        return svc;
    }

    private Map<String, Object> row(Object softDemoteUntil) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", "123");
        r.put("title", "标题");
        r.put("coverUrl", null);
        r.put("mediaType", "text");
        r.put("softDemoteUntil", softDemoteUntil);
        return r;
    }

    @Test
    @DisplayName("MySQL 形态（LocalDateTime）：未过期保留下发、过期置 null，不抛 CCE")
    void mysqlShape() {
        LocalDateTime future = LocalDateTime.now().plusDays(30);
        LocalDateTime past = LocalDateTime.now().minusHours(1);
        var svc = newService(new ArrayList<>(List.of(row(future), row(past), row(null))));
        Map<String, Object> out = svc.mine(1, null);
        @SuppressWarnings("unchecked")
        var records = (List<Map<String, Object>>) out.get("records");
        assertEquals(future, records.get(0).get("softDemoteUntil"), "MySQL 形态未过期必须保留下发");
        assertNull(records.get(1).get("softDemoteUntil"), "已过期一律置 null");
        assertNull(records.get(2).get("softDemoteUntil"), "本就 null 不得抛异常");
    }

    @Test
    @DisplayName("H2 形态（Timestamp）：行为与 MySQL 形态一致")
    void h2Shape() {
        LocalDateTime future = LocalDateTime.now().plusDays(30);
        var svc = newService(new ArrayList<>(List.of(row(Timestamp.valueOf(future)))));
        Map<String, Object> out = svc.mine(1, null);
        @SuppressWarnings("unchecked")
        var records = (List<Map<String, Object>>) out.get("records");
        // 驱动原样对象留在 map 里（Timestamp 与 LocalDateTime 不同类），按统一转换后比较等值
        assertEquals(future, com.zhiyu.common.util.JdbcTimes.toLocalDateTime(records.get(0).get("softDemoteUntil")));
    }
}
