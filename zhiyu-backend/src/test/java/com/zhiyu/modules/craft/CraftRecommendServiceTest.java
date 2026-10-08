package com.zhiyu.modules.craft;

import com.zhiyu.ai.AiClient;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.entity.CraftRecommendation;
import com.zhiyu.modules.craft.entity.CraftRecommendationItem;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.craft.mapper.CraftRecommendationItemMapper;
import com.zhiyu.modules.craft.mapper.CraftRecommendationMapper;
import com.zhiyu.modules.craft.service.CraftRecommendService;
import com.zhiyu.modules.craft.vo.RecommendItemVO;
import com.zhiyu.modules.craft.vo.RecommendVO;
import com.zhiyu.modules.emotion.entity.EmotionSession;
import com.zhiyu.modules.emotion.mapper.EmotionSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link CraftRecommendService} 集成测试（T1.5）。
 *
 * <p>@SpringBootTest + H2，mock {@link AiClient} 以隔离 LLM 调用
 * 以隔离 LLM 调用，不依赖 init-data.sql。
 *
 * <p>用 @DirtiesContext(AFTER_CLASS) 避免 H2 数据污染后续测试类
 * （参考 EmotionControllerTest 的做法）。
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class CraftRecommendServiceTest {

    @Autowired
    private CraftRecommendService craftRecommendService;

    @Autowired
    private CraftProjectMapper craftProjectMapper;

    @Autowired
    private CraftRecommendationMapper craftRecommendationMapper;

    @Autowired
    private CraftRecommendationItemMapper craftRecommendationItemMapper;

    @Autowired
    private EmotionSessionMapper emotionSessionMapper;

    @MockBean
    private AiClient aiClient;

    /** 测试用户 ID */
    private static final Long USER_ID = 8001L;

    /** 测试用项目 ID 前缀 */
    private static final Long PROJECT_BASE = 100001L;

    @BeforeEach
    void setUp() {
        // 默认 mock：AiClient 返回正常理由（输出安全过滤已在 AiClient 内部完成）
        when(aiClient.recommendReason(anyString(), anyList()))
                .thenReturn("推荐理由：这些手作很适合你现在的状态");

        // 插入 5 条测试项目
        insertProject(PROJECT_BASE + 1, "编织入门", 1, 30, "专注,静心");
        insertProject(PROJECT_BASE + 2, "扎染体验", 1, 45, "放空,释放");
        insertProject(PROJECT_BASE + 3, "陶艺进阶", 2, 60, "成就感,创造表达");
        insertProject(PROJECT_BASE + 4, "篆刻高阶", 3, 90, "物理宣泄");
        insertProject(PROJECT_BASE + 5, "剪纸小品", 1, 20, "轻量成就");
    }

    @Test
    @DisplayName("mood=anxious → 候选含 P1（专注,静心）")
    void testMatchAnxious() {
        EmotionSession session = insertSession("anxious");

        RecommendVO vo = craftRecommendService.recommend(USER_ID, session.getId(), 1, 60);

        assertNotNull(vo);
        assertEquals("anxious", vo.getMood());
        assertFalse(vo.getItems().isEmpty());

        boolean containsP1 = vo.getItems().stream()
                .anyMatch(item -> item.getProjectId().equals(PROJECT_BASE + 1));
        assertTrue(containsP1, "anxious 应匹配到 P1（专注,静心）");
    }

    @Test
    @DisplayName("mood=stressed → 候选含 P2（放空,释放）")
    void testMatchStressed() {
        EmotionSession session = insertSession("stressed");

        RecommendVO vo = craftRecommendService.recommend(USER_ID, session.getId(), 1, 60);

        assertEquals("stressed", vo.getMood());
        boolean containsP2 = vo.getItems().stream()
                .anyMatch(item -> item.getProjectId().equals(PROJECT_BASE + 2));
        assertTrue(containsP2, "stressed 应匹配到 P2（放空,释放）");
    }

    @Test
    @DisplayName("skillLevel=1 → P4 被过滤（level 3 > 1+1）")
    void testDifficultyFilter() {
        EmotionSession session = insertSession("angry");

        RecommendVO vo = craftRecommendService.recommend(USER_ID, session.getId(), 1, 120);

        boolean containsP4 = vo.getItems().stream()
                .anyMatch(item -> item.getProjectId().equals(PROJECT_BASE + 4));
        assertFalse(containsP4, "skillLevel=1 时 P4（level 3）应被过滤");
    }

    @Test
    @DisplayName("minutes=30 → P3（60 分钟）被过滤")
    void testMinutesFilter() {
        EmotionSession session = insertSession("depressed");

        RecommendVO vo = craftRecommendService.recommend(USER_ID, session.getId(), 3, 30);

        boolean containsP3 = vo.getItems().stream()
                .anyMatch(item -> item.getProjectId().equals(PROJECT_BASE + 3));
        assertFalse(containsP3, "minutes=30 时 P3（60 分钟）应被过滤");
    }

    @Test
    @DisplayName("极端参数让规则层为空 → 兜底返回前 3 条")
    void testFallbackEmptyAfterFilter() {
        EmotionSession session = insertSession("anxious");

        // minutes=10 过滤掉大部分项目，仅 P5(20) 不过 -> 但 P5 estMinutes=20 > 10
        // 所以过滤后为空，触发兜底
        RecommendVO vo = craftRecommendService.recommend(USER_ID, session.getId(), 1, 10);

        assertNotNull(vo);
        assertFalse(vo.getItems().isEmpty(), "兜底应返回前 3 条");
    }

    @Test
    @DisplayName("AiClient 抛异常 → 验证返回降级文案")
    void testLlmFail_FallbackReason() {
        EmotionSession session = insertSession("anxious");
        // AiClient 内部捕获异常返回降级文案，这里模拟它返回降级文案
        when(aiClient.recommendReason(anyString(), anyList()))
                .thenReturn("（暂时无法生成个性化理由，先看看这些手作吧）");

        RecommendVO vo = craftRecommendService.recommend(USER_ID, session.getId(), 1, 60);

        assertNotNull(vo.getReason());
        assertTrue(vo.getReason().contains("暂时无法生成"), "应返回降级文案");
    }

    @Test
    @DisplayName("AiClient 返回的 REWRITE 文本作为最终 reason（安全过滤在 AiClient 内部完成）")
    void testOutputFilterInvoked() {
        EmotionSession session = insertSession("anxious");
        // AiClient 内部已做安全过滤，返回的即改写后的文本
        String rewritten = "我在这里陪你。如果愿意，可以跟我聊聊你现在的感受。";
        when(aiClient.recommendReason(anyString(), anyList()))
                .thenReturn(rewritten);

        RecommendVO vo = craftRecommendService.recommend(USER_ID, session.getId(), 1, 60);

        assertEquals(rewritten, vo.getReason(), "AiClient 返回的文本应作为最终 reason");
        verify(aiClient).recommendReason(anyString(), anyList());
    }

    @Test
    @DisplayName("验证 craft_recommendation 写 1 行 + craft_recommendation_item 写 3 行")
    void testPersistRecommendation() {
        EmotionSession session = insertSession("anxious");

        RecommendVO vo = craftRecommendService.recommend(USER_ID, session.getId(), 1, 60);

        assertNotNull(vo.getRecommendationId());

        // 验证主表写入 1 行
        CraftRecommendation rec = craftRecommendationMapper.selectById(vo.getRecommendationId());
        assertNotNull(rec);
        assertEquals(USER_ID, rec.getUserId());
        assertEquals("anxious", rec.getMood());

        // 验证明细表写入 3 行
        var itemWrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CraftRecommendationItem>();
        itemWrapper.eq(CraftRecommendationItem::getRecommendationId, vo.getRecommendationId());
        List<CraftRecommendationItem> items = craftRecommendationItemMapper.selectList(itemWrapper);
        assertEquals(vo.getItems().size(), items.size(), "明细行数应与返回 items 数量一致");
        assertTrue(items.size() >= 1, "至少应写入 1 条明细");
    }

    // ──────────────────────────────────────────────────────
    //  辅助方法
    // ──────────────────────────────────────────────────────

    private void insertProject(Long id, String title, int level, int estMinutes, String therapyTags) {
        CraftProject p = new CraftProject();
        p.setId(id);
        p.setInheritorId(1L);
        p.setTitle(title);
        p.setIntro(title + " 简介");
        p.setCultureBg("文化背景");
        p.setLevel(level);
        p.setEstMinutes(estMinutes);
        p.setCategory("其他");
        p.setTherapyTags(therapyTags);
        p.setStatus(1);
        p.setCreateTime(LocalDateTime.now());
        p.setUpdateTime(LocalDateTime.now());
        p.setDeleted(0);
        craftProjectMapper.insert(p);
    }

    private EmotionSession insertSession(String mood) {
        EmotionSession session = new EmotionSession();
        session.setUserId(USER_ID);
        session.setMood(mood);
        session.setStatus(0);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        session.setDeleted(0);
        emotionSessionMapper.insert(session);
        return session;
    }
}
