package com.zhiyu.modules.craft;

import com.zhiyu.ai.AiClient;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.craft.mapper.CraftRecommendationItemMapper;
import com.zhiyu.modules.craft.mapper.CraftRecommendationMapper;
import com.zhiyu.modules.craft.service.impl.CraftRecommendServiceImpl;
import com.zhiyu.modules.craft.vo.RecommendVO;
import com.zhiyu.modules.emotion.mapper.EmotionSessionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link CraftRecommendServiceImpl} 的 LLM 交互 Mockito 单元测试（T1.5）。
 *
 * <p>验证 AiClient 和 ContentSafetyFilter 的调用契约，不启动 Spring 上下文。
 */
@ExtendWith(MockitoExtension.class)
class CraftRecommendServiceLlmTest {
    @Mock com.zhiyu.modules.admin.service.RuntimeSettings settings;
    @Mock org.springframework.jdbc.core.JdbcTemplate db;
    @org.junit.jupiter.api.BeforeEach void configuration(){when(settings.values("recommendation")).thenReturn(java.util.Map.of("maxSafety","GUIDED"));when(settings.number(eq("recommendation"),anyString())).thenAnswer(i->switch((String)i.getArgument(1)){case "count"->3.0;case "tagWeight"->0.6;default->0.4;});when(db.queryForList(anyString(),eq(Long.class),eq("GUIDED"))).thenReturn(List.of(101L,102L,201L));}


    @Mock
    CraftProjectMapper craftProjectMapper;
    @Mock
    CraftRecommendationMapper craftRecommendationMapper;
    @Mock
    CraftRecommendationItemMapper craftRecommendationItemMapper;
    @Mock
    EmotionSessionMapper emotionSessionMapper;
    @Mock
    AiClient aiClient;

    @InjectMocks
    CraftRecommendServiceImpl service;

    @Test
    @DisplayName("AiClient.recommendReason 被调用，参数含 mood 和项目名列表")
    void testAiClientCalledWithMoodAndCraftNames() {
        // 准备：emotion session 返回 mood
        com.zhiyu.modules.emotion.entity.EmotionSession session = new com.zhiyu.modules.emotion.entity.EmotionSession();
        session.setMood("anxious");
        session.setUserId(1L);
        when(emotionSessionMapper.selectById(anyLong())).thenReturn(session);

        // 准备：craft_project 返回 2 条上架项目
        CraftProject p1 = new CraftProject();
        p1.setId(101L);
        p1.setTitle("编织入门");
        p1.setLevel(1);
        p1.setEstMinutes(30);
        p1.setTherapyTags("专注,静心");
        p1.setStatus(1);
        CraftProject p2 = new CraftProject();
        p2.setId(102L);
        p2.setTitle("扎染体验");
        p2.setLevel(1);
        p2.setEstMinutes(45);
        p2.setTherapyTags("放空,释放");
        p2.setStatus(1);
        when(craftProjectMapper.selectList(any())).thenReturn(List.of(p1, p2));

        // 准备：AiClient 返回正常理由（输出安全过滤已在 AiClient 内部完成）
        when(aiClient.recommendReason(anyString(), anyList())).thenReturn("推荐理由");

        // 执行
        RecommendVO vo = service.recommend(1L, 999L, 1, 60);

        // 验证 AiClient 被调用，参数含 mood 和项目标题
        verify(aiClient).recommendReason(eq("anxious"), argThat(names -> names.contains("编织入门") && names.contains("扎染体验")));
        assertNotNull(vo.getReason());
    }

    @Test
    @DisplayName("AiClient.recommendReason 返回后的理由直接作为最终结果（安全过滤在 AiClient 内部完成）")
    void testCheckOutputCalledAfterAi() {
        // 准备
        com.zhiyu.modules.emotion.entity.EmotionSession session = new com.zhiyu.modules.emotion.entity.EmotionSession();
        session.setMood("stressed");
        session.setUserId(1L);
        when(emotionSessionMapper.selectById(anyLong())).thenReturn(session);

        CraftProject p1 = new CraftProject();
        p1.setId(201L);
        p1.setTitle("扎染体验");
        p1.setLevel(1);
        p1.setEstMinutes(45);
        p1.setTherapyTags("放空,释放");
        p1.setStatus(1);
        when(craftProjectMapper.selectList(any())).thenReturn(List.of(p1));

        // AiClient 内部已包含安全过滤，返回的即最终文本
        when(aiClient.recommendReason(anyString(), anyList())).thenReturn("AI 生成的理由（已过安全过滤）");

        // 执行
        RecommendVO vo = service.recommend(1L, 999L, 1, 60);

        // 验证 AiClient 被调用，返回的 reason 就是 AiClient 的返回值
        verify(aiClient).recommendReason(anyString(), anyList());
        assertEquals("AI 生成的理由（已过安全过滤）", vo.getReason());
    }
}
