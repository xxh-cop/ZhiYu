package com.zhiyu.modules.companion.memory;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 MySQL 冒烟：验证「小织记得的」的加密读写、派生统计 SQL、对话注入与删除边界。
 *
 * <p>只在显式设置 {@code RUN_REAL_DB_IT=true} 时运行，避免 CI/日常单测依赖本机数据库。
 * 用例带 {@link Transactional}，结束后回滚，不向开发库留下数据。
 *
 * <pre>
 * $env:RUN_REAL_DB_IT='true'
 * mvn test "-Dtest=CompanionMemoryRealDbIT" "-Dmaven.repo.local=../.ai-tmp/m2"
 * </pre>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_REAL_DB_IT", matches = "true")
class CompanionMemoryRealDbIT {

    @Autowired
    private JdbcTemplate db;
    @Autowired
    private CompanionMemoryService service;
    @Autowired
    private AesEncryptor crypto;

    private long userId;

    @BeforeEach
    void setUp() {
        userId = IdWorker.getId();
        db.update("""
                INSERT INTO user (id, username, password, nickname, can_contact, privacy_agreed, role, status,
                                  create_time, update_time, deleted)
                VALUES (?, ?, ?, ?, 0, 1, 0, 1, NOW(), NOW(), 0)
                """, userId, "it_mem_" + userId, "$2a$10$smoke-only-not-a-real-hash", "冒烟用户");
        UserContext.set(new LoginUser(userId, 0));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void memoryWorksAgainstRealMySql() {
        // 1. 初始关闭
        Map<String, Object> initial = service.get();
        assertFalse((Boolean) initial.get("enabled"));
        assertTrue(((List<?>) initial.get("items")).isEmpty());
        assertEquals("memory-v2", initial.get("consentVersion"));

        // 2. 开启 + 保存偏好与回避话题
        service.enable(true, true);
        Map<String, Object> saved = service.save(true, Map.of(
                "preferred_minutes", "10",
                "preferred_material", "bamboo",
                "preferred_pattern_family", "stitch",
                "avoid_topics", "考试, 家庭"));
        assertTrue((Boolean) saved.get("enabled"));
        assertEquals(3, ((List<?>) saved.get("items")).size());
        assertEquals(List.of("考试", "家庭"), saved.get("avoidTopics"));

        // 3. 落库确为密文，且能解回明文
        String cipher = db.queryForObject(
                "SELECT value_cipher FROM user_ai_memory WHERE user_id=? AND memory_key='avoid_topics' AND deleted=0",
                String.class, userId);
        assertFalse(cipher.contains("考试"), "回避话题不应以明文落库");
        assertTrue(crypto.decrypt(cipher).contains("考试"), "密文应能解回原文");

        // 4. 造真实统计数据：3 次完成手作（晚上）、2 次呼吸响应、2 个低落日期
        LocalDateTime base = LocalDateTime.now().withHour(20).withMinute(0).withSecond(0).withNano(0);
        for (int i = 1; i <= 3; i++) {
            long sessionId = IdWorker.getId();
            LocalDateTime start = base.minusDays(i);
            db.update("""
                    INSERT INTO craft_session (id, user_id, project_id, status, current_step, start_time, end_time,
                                               flow_seconds, create_time, update_time, deleted)
                    VALUES (?, ?, 1, 3, 3, ?, ?, 0, NOW(), NOW(), 0)
                    """, sessionId, userId, start, start.plusMinutes(10));
        }
        for (int i = 1; i <= 2; i++) {
            long sessionId = db.queryForObject(
                    "SELECT id FROM craft_session WHERE user_id=? ORDER BY start_time DESC LIMIT 1",
                    Long.class, userId);
            db.update("""
                    INSERT INTO craft_session_event (id, session_id, user_id, craft_project_id, event_type,
                                                     occurred_at, create_time, update_time, deleted)
                    VALUES (?, ?, ?, 1, 'breathing_responded', ?, NOW(), NOW(), 0)
                    """, IdWorker.getId(), sessionId, userId, base.minusDays(i));
        }
        for (int i = 1; i <= 2; i++) {
            db.update("""
                    INSERT INTO emotion_note (id, user_id, content, mood, note_date, create_time, update_time, deleted)
                    VALUES (?, ?, NULL, 'tired', ?, NOW(), NOW(), 0)
                    """, IdWorker.getId(), userId, base.minusDays(i).toLocalDate());
        }

        // 5. 派生档案必须来自真实 SQL，且没有降级为空
        Map<String, Object> after = service.get();
        List<Map<String, String>> derived = (List<Map<String, String>>) after.get("derived");
        assertEquals(4, derived.size(), "真实 SQL 应产出 4 项档案");
        Map<String, String> calm = byKey(derived, "calm_strategy");
        assertTrue(calm.get("value").contains("呼吸引导"), "呼吸响应 ≥2 次应优先建议呼吸引导");
        assertEquals("3 件", byKey(derived, "completed_crafts").get("value"));
        assertEquals("晚上（18–23 点）", byKey(derived, "preferred_time_window").get("value"));
        assertEquals("2 天", byKey(derived, "recent_low_days").get("value"));

        // 6. 对话注入：最多 3 条，回避话题在前，低落天数不外泄
        Map<String, Object> chat = service.chatProfile(userId);
        List<String> lines = (List<String>) chat.get("lines");
        assertTrue(lines.size() <= 3);
        assertTrue(lines.get(0).contains("考试"));
        assertTrue(lines.stream().anyMatch(l -> l.contains("呼吸引导")));
        assertFalse(lines.stream().anyMatch(l -> l.contains("低落")), "低落天数只展示，不进对话");

        // 7. 删除边界
        service.deleteItem("avoid_topics");
        assertTrue(((List<?>) service.get().get("avoidTopics")).isEmpty());
        BusinessException derivedDelete = assertThrows(BusinessException.class,
                () -> service.deleteItem("calm_strategy"));
        assertEquals(Integer.valueOf(1001), derivedDelete.getCode());

        // 8. 整份删除
        Map<String, Object> cleared = service.deleteAll();
        assertFalse((Boolean) cleared.get("enabled"));
        assertEquals(0, db.queryForObject(
                "SELECT COUNT(*) FROM user_ai_memory WHERE user_id=? AND deleted=0", Integer.class, userId));
    }

    private Map<String, String> byKey(List<Map<String, String>> derived, String key) {
        return derived.stream().filter(d -> key.equals(d.get("key"))).findFirst().orElseThrow();
    }
}
