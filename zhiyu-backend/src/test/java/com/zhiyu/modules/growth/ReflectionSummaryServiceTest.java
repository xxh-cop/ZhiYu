package com.zhiyu.modules.growth;

import com.zhiyu.ai.AiClient;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReflectionSummaryServiceTest {

    private static final long USER_ID = 2L;
    private static final long SESSION_ID = 9001L;

    private JdbcTemplate db;
    private AiClient aiClient;
    private ReflectionSummaryService service;

    @BeforeEach
    void setUp() throws Exception {
        var ds = new DriverManagerDataSource(
                "jdbc:h2:mem:reflection" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
                "sa", "");
        try (var connection = ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new org.springframework.core.io.FileSystemResource("src/test/resources/schema-h2.sql"));
        }
        db = new JdbcTemplate(ds);
        aiClient = mock(AiClient.class);
        service = new ReflectionSummaryService(db, aiClient,
                new ClassPathResource("prompts/reflection.st"));
        db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted) VALUES(?,?,?,?,?,?,?,0)",
                USER_ID, "reflection_student", "hash", "学生", 0, 1, 1);
        db.update("INSERT INTO craft_project(id,inheritor_id,title,status,deleted) VALUES(?,?,?,?,0)",
                8001L, 1L, "折扇小景", 1);
        db.update("INSERT INTO craft_session(id,user_id,project_id,status,current_step,deleted) VALUES(?,?,?,?,?,0)",
                SESSION_ID, USER_ID, 8001L, 3, 4);
        for (int i = 1; i <= 4; i++) {
            db.update("INSERT INTO craft_step(id,project_id,step_no,title,deleted) VALUES(?,?,?,?,0)",
                    8100L + i, 8001L, i, "步骤" + i);
        }
        UserContext.set(new LoginUser(USER_ID, 0));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("AI 可用时返回生成回顾，只携带自填信息")
    void returnsGeneratedSummaryWithoutChatContent() {
        when(aiClient.reflectionSummary(anyString(), anyString())).thenReturn("你完成了四个步骤，并记录下创作前后的感受。");

        Map<String, Object> result = service.generate(request(true, "平静", "数着折线慢慢安静下来"));

        assertEquals("你完成了四个步骤，并记录下创作前后的感受。", result.get("summary"));
        assertEquals(true, result.get("generated"));
        verify(aiClient).reflectionSummary(contains("折扇小景"), contains("数着折线慢慢安静下来"));
    }

    @Test
    @DisplayName("AI 不可用时返回固定模板且 generated=false")
    void fallsBackWhenAiUnavailable() {
        when(aiClient.reflectionSummary(anyString(), anyString())).thenReturn(null);

        Map<String, Object> result = service.generate(request(true, "平静", ""));

        assertTrue(((String) result.get("summary")).contains("折扇小景"));
        assertTrue(((String) result.get("summary")).contains("4 个步骤"));
        assertEquals(false, result.get("generated"));
    }

    @Test
    @DisplayName("过长输出回退固定模板")
    void overlongOutputFallsBack() {
        when(aiClient.reflectionSummary(anyString(), anyString())).thenReturn("很长".repeat(160));

        Map<String, Object> result = service.generate(request(true, "平静", ""));

        assertEquals(false, result.get("generated"));
    }

    @Test
    @DisplayName("未勾选 AI 同意不调用模型")
    void requiresAiConsent() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.generate(request(false, "平静", "")));
        assertEquals(1001, error.getCode());
        verifyNoInteractions(aiClient);
    }

    @Test
    @DisplayName("不能生成他人或未完成创作的回顾")
    void rejectsOtherUserAndIncompleteSession() {
        UserContext.set(new LoginUser(3L, 0));
        assertEquals(1004, assertThrows(BusinessException.class,
                () -> service.generate(request(true, "平静", ""))).getCode());

        UserContext.set(new LoginUser(USER_ID, 0));
        db.update("UPDATE craft_session SET status=1 WHERE id=?", SESSION_ID);
        assertEquals(3001, assertThrows(BusinessException.class,
                () -> service.generate(request(true, "平静", ""))).getCode());
    }

    @Test
    @DisplayName("未同意隐私政策不生成")
    void requiresPrivacyConsent() {
        db.update("UPDATE user SET privacy_agreed=0 WHERE id=?", USER_ID);
        assertEquals(1001, assertThrows(BusinessException.class,
                () -> service.generate(request(true, "平静", ""))).getCode());
        verifyNoInteractions(aiClient);
    }

    private GrowthRequests.ReflectionSummary request(boolean consent, String emotion, String note) {
        return new GrowthRequests.ReflectionSummary(SESSION_ID, "期末周的一片叶子", 2, 4, emotion, note, consent);
    }
}
