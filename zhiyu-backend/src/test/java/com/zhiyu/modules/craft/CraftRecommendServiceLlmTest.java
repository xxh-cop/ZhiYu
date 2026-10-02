package com.zhiyu.modules.craft;

import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.ai.safety.SafetyResult;
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
    @Mock
    ContentSafetyFilter contentSafetyFilter;

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

        // 准备：AiClient 返回正常理由
        when(aiClient.recommendReason(anyString(), anyList())).thenReturn("推荐理由");
        when(contentSafetyFilter.checkOutput(anyLong(), anyString(), anyString()))
                .thenReturn(SafetyResult.pass("通过"));

        // 执行
        RecommendVO vo = service.recommend(1L, 999L, 1, 60);

        // 验证 AiClient 被调用，参数含 mood 和项目标题
        verify(aiClient).recommendReason(eq("anxious"), argThat(names -> names.contains("编织入门") && names.contains("扎染体验")));
        assertNotNull(vo.getReason());
    }

    @Test
    @DisplayName("ContentSafetyFilter.checkOutput 在 AI 返回后被调用")
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

        when(aiClient.recommendReason(anyString(), anyList())).thenReturn("AI 生成的理由");
        when(contentSafetyFilter.checkOutput(anyLong(), anyString(), anyString()))
                .thenReturn(SafetyResult.pass("通过"));

        // 执行
        service.recommend(1L, 999L, 1, 60);

        // 验证 checkOutput 在 AiClient 之后被调用
        verify(aiClient).recommendReason(anyString(), anyList());
        verify(contentSafetyFilter).checkOutput(eq(1L), eq("recommend"), eq("AI 生成的理由"));
    }
}
