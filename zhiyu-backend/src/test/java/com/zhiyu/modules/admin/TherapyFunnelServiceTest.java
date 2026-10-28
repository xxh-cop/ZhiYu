package com.zhiyu.modules.admin;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.admin.service.TherapyFunnelService;
import com.zhiyu.modules.admin.vo.TherapyFunnelVO;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TherapyFunnelServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long ADMIN_ID = 1L;
    private static final long STUDENT_BASE = 201L;
    private static final long EMOTION_BASE = 1001L;

    private JdbcTemplate db;
    private TherapyFunnelService service;
    private LocalDateTime yesterday;

    @BeforeEach
    void setup() throws Exception {
        var ds = new DriverManagerDataSource(
                "jdbc:h2:mem:funnel" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
                "sa", "");
        try (var connection = ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("../sql/schema-h2.sql"));
        }
        db = new JdbcTemplate(ds);
        service = new TherapyFunnelService(db);
        yesterday = LocalDate.now(ZONE).minusDays(1).atTime(10, 0);

        db.update("INSERT INTO user(id,username,password,nickname,role,status,deleted) VALUES(?,?,?,?,?,?,0)",
                ADMIN_ID, "funnel_admin", "hash", "Admin", 3, 1);
        for (int i = 0; i < 6; i++) {
            long userId = STUDENT_BASE + i;
            db.update("INSERT INTO user(id,username,password,nickname,role,status,deleted) VALUES(?,?,?,?,?,?,0)",
                    userId, "funnel_student_" + i, "hash", "Student" + i, 0, 1);
            db.update("INSERT INTO emotion_session(id,user_id,mood,status,create_time,update_time,deleted) VALUES(?,?,?,?,?,?,0)",
                    EMOTION_BASE + i, userId, "stressed", 1, yesterday, yesterday);
        }

        // 3 人获得推荐；2 人点击；2 人开始创作；1 人完成并提交作品。
        for (int i = 0; i < 3; i++) {
            db.update("INSERT INTO craft_recommendation(id,user_id,mood,reason,create_time,update_time,deleted) VALUES(?,?,?,?,?,?,0)",
                    3001 + i, STUDENT_BASE + i, "stressed", "test", yesterday.plusHours(1), yesterday.plusHours(1));
        }
        for (int i = 0; i < 2; i++) {
            db.update("INSERT INTO craft_recommend_event(id,user_id,recommendation_id,project_id,event_type,source,create_time,update_time,deleted) VALUES(?,?,?,?,?,?,?,?,0)",
                    4001 + i, STUDENT_BASE + i, 3001 + i, 940001, "CLICK", "HOME",
                    yesterday.plusHours(2), yesterday.plusHours(2));
        }
        db.update("INSERT INTO craft_session(id,user_id,project_id,status,start_time,end_time,flow_seconds,create_time,update_time,deleted) VALUES(?,?,?,?,?,?,?,?,?,0)",
                5001, STUDENT_BASE, 940001, 1, yesterday.plusHours(3), null, 0, yesterday.plusHours(3), yesterday.plusHours(3));
        db.update("INSERT INTO craft_session(id,user_id,project_id,status,start_time,end_time,flow_seconds,create_time,update_time,deleted) VALUES(?,?,?,?,?,?,?,?,?,0)",
                5002, STUDENT_BASE + 1, 940001, 3, yesterday.plusHours(3), yesterday.plusHours(3).plusSeconds(100), 60,
                yesterday.plusHours(3), yesterday.plusHours(3));
        db.update("INSERT INTO work(id,user_id,project_id,session_id,title,media_type,visible,anonymous,status,create_time,update_time,deleted) VALUES(?,?,?,?,?,?,?,?,?,?,?,0)",
                6001, STUDENT_BASE + 1, 940001, 5002, "test work", "text", 1, 1, 1,
                yesterday.plusHours(4), yesterday.plusHours(4));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("同一批用户六阶段计数、转化率与心流占比")
    void countsSameCohortAndFlowRate() {
        UserContext.set(new LoginUser(ADMIN_ID, 3));
        TherapyFunnelVO result = service.funnel(7);

        assertEquals(7, result.getDays());
        assertEquals(6, result.getCohort());
        assertEquals(false, result.isInsufficient());
        assertEquals(6, stage(result, "emotion").getUsers());
        assertEquals(3, stage(result, "recommended").getUsers());
        assertEquals(2, stage(result, "clicked").getUsers());
        assertEquals(2, stage(result, "started").getUsers());
        assertEquals(1, stage(result, "completed").getUsers());
        assertEquals(1, stage(result, "published").getUsers());
        assertEquals(0.5, stage(result, "recommended").getFromPrevious(), 0.0001);
        assertEquals(0.6, result.getFlow().getFocusRate(), 0.0001);
        assertEquals(1, result.getFlow().getCompletedSessions());
        assertTrue(!result.toString().contains("userId"));
    }

    @Test
    @DisplayName("cohort 少于 5 时不计算转化率")
    void insufficientCohortHidesRates() {
        db.update("DELETE FROM work");
        db.update("DELETE FROM craft_session");
        db.update("DELETE FROM craft_recommend_event");
        db.update("DELETE FROM craft_recommendation");
        db.update("DELETE FROM emotion_session");
        db.update("INSERT INTO emotion_session(id,user_id,mood,status,create_time,update_time,deleted) VALUES(?,?,?,?,?,?,0)",
                7001, STUDENT_BASE, "stressed", 1, yesterday, yesterday);
        db.update("INSERT INTO emotion_session(id,user_id,mood,status,create_time,update_time,deleted) VALUES(?,?,?,?,?,?,0)",
                7002, STUDENT_BASE + 1, "stressed", 1, yesterday, yesterday);

        UserContext.set(new LoginUser(ADMIN_ID, 3));
        TherapyFunnelVO result = service.funnel(7);

        assertEquals(2, result.getCohort());
        assertTrue(result.isInsufficient());
        assertNull(stage(result, "emotion").getFromStart());
        assertNull(stage(result, "recommended").getFromPrevious());
    }

    @Test
    @DisplayName("非管理员与非法时间范围被拒绝")
    void rejectsNonAdminAndInvalidRange() {
        UserContext.set(new LoginUser(STUDENT_BASE, 0));
        assertEquals(1003, assertThrows(BusinessException.class, () -> service.funnel(7)).getCode());
        UserContext.set(new LoginUser(ADMIN_ID, 3));
        assertEquals(1001, assertThrows(BusinessException.class, () -> service.funnel(90)).getCode());
    }

    private TherapyFunnelVO.Stage stage(TherapyFunnelVO result, String key) {
        return result.getStages().stream()
                .filter(item -> key.equals(item.getKey()))
                .findFirst()
                .orElseThrow();
    }
}
