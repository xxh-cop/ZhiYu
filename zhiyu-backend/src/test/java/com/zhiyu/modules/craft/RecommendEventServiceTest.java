package com.zhiyu.modules.craft;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.craft.dto.RecommendClickDTO;
import com.zhiyu.modules.craft.service.RecommendEventService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecommendEventServiceTest {

    private static final long USER_ID = 91001L;
    private static final long RECOMMENDATION_ID = 91002L;
    private static final long PROJECT_ID = 91003L;

    @Autowired
    private RecommendEventService service;

    @Autowired
    private JdbcTemplate db;

    @BeforeEach
    void setUp() {
        db.update("INSERT INTO craft_recommendation(id,user_id,mood,reason,deleted) VALUES(?,?,?,?,0)",
                RECOMMENDATION_ID, USER_ID, "stressed", "test");
        db.update("INSERT INTO craft_recommendation_item(id,recommendation_id,project_id,score,deleted) VALUES(?,?,?,?,0)",
                RECOMMENDATION_ID + 100, RECOMMENDATION_ID, PROJECT_ID, 1.0);
        UserContext.set(new LoginUser(USER_ID, 0));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("首次点击写入事件，重复点击幂等")
    void clickIsIdempotent() {
        RecommendClickDTO dto = dto(USER_ID, RECOMMENDATION_ID, PROJECT_ID, "HOME");
        service.click(USER_ID, dto);
        service.click(USER_ID, dto);
        assertEquals(1, count());
    }

    @Test
    @DisplayName("不能点击他人的推荐")
    void rejectsOtherUsersRecommendation() {
        RecommendClickDTO dto = dto(USER_ID + 1, RECOMMENDATION_ID, PROJECT_ID, "RECOMMEND");
        BusinessException error = assertThrows(BusinessException.class, () -> service.click(USER_ID + 1, dto));
        assertEquals(1004, error.getCode());
        assertEquals(0, count());
    }

    @Test
    @DisplayName("项目必须属于该推荐")
    void rejectsUnknownProject() {
        RecommendClickDTO dto = dto(USER_ID, RECOMMENDATION_ID, PROJECT_ID + 1, "HOME");
        BusinessException error = assertThrows(BusinessException.class, () -> service.click(USER_ID, dto));
        assertEquals(1004, error.getCode());
    }

    @Test
    @DisplayName("来源只能是 HOME 或 RECOMMEND")
    void rejectsUnknownSource() {
        RecommendClickDTO dto = dto(USER_ID, RECOMMENDATION_ID, PROJECT_ID, "OTHER");
        BusinessException error = assertThrows(BusinessException.class, () -> service.click(USER_ID, dto));
        assertEquals(1001, error.getCode());
    }

    private RecommendClickDTO dto(long userId, long recommendationId, long projectId, String source) {
        RecommendClickDTO dto = new RecommendClickDTO();
        dto.setRecommendationId(String.valueOf(recommendationId));
        dto.setProjectId(String.valueOf(projectId));
        dto.setSource(source);
        return dto;
    }

    private int count() {
        return db.queryForObject("SELECT COUNT(*) FROM craft_recommend_event WHERE user_id=? AND recommendation_id=? AND project_id=? AND event_type='CLICK'",
                Integer.class, USER_ID, RECOMMENDATION_ID, PROJECT_ID);
    }
}
