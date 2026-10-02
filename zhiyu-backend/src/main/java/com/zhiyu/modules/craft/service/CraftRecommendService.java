package com.zhiyu.modules.craft.service;

import com.zhiyu.modules.craft.vo.RecommendVO;

/**
 * 手作推荐服务（T1.5）。
 *
 * <p>规则层 + LLM 层混合推荐：
 * <ol>
 *   <li>从情绪会话推断 mood</li>
 *   <li>规则层按 mood → therapy_tags 匹配 + 难度/时长过滤 → Top 3</li>
 *   <li>LLM 生成整体推荐理由（走 {@link com.zhiyu.ai.AiClient} 门面 + 熔断）</li>
 *   <li>输出经 {@link com.zhiyu.ai.safety.ContentSafetyFilter#checkOutput} 过滤</li>
 *   <li>落库 craft_recommendation + craft_recommendation_item</li>
 * </ol>
 */
public interface CraftRecommendService {

    /**
     * 根据当前心情推荐手作。
     *
     * @param userId     当前用户 ID
     * @param sessionId  情绪会话 ID（可空，为空时取用户最近一条会话的 mood）
     * @param skillLevel 用户技能等级 1-3，过滤 level <= skillLevel+1
     * @param minutes    可用时间（分钟），过滤 estMinutes <= minutes
     * @return 推荐结果
     */
    RecommendVO recommend(Long userId, Long sessionId, Integer skillLevel, Integer minutes);
}
