package com.zhiyu.modules.craft.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.ai.AiClient;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.entity.CraftRecommendation;
import com.zhiyu.modules.craft.entity.CraftRecommendationItem;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.craft.mapper.CraftRecommendationItemMapper;
import com.zhiyu.modules.craft.mapper.CraftRecommendationMapper;
import com.zhiyu.modules.craft.service.CraftRecommendService;
import com.zhiyu.modules.craft.service.RecommendRuleConfig;
import com.zhiyu.modules.craft.vo.RecommendItemVO;
import com.zhiyu.modules.craft.vo.RecommendVO;
import com.zhiyu.modules.emotion.entity.EmotionSession;
import com.zhiyu.modules.emotion.mapper.EmotionSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 手作推荐服务实现（T1.5）。
 *
 * <p>规则层 + LLM 层混合推荐，流程见 {@link CraftRecommendService}。
 *
 * <p>安全过滤：LLM 返回的整体理由由 {@link AiClient#recommendReason} 内部
 * 自动做输出安全过滤（AGENTS.md §4），本类不再显式调用 ContentSafetyFilter。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CraftRecommendServiceImpl implements CraftRecommendService {

    private final com.zhiyu.modules.admin.service.RuntimeSettings settings;
    private final org.springframework.jdbc.core.JdbcTemplate db;
    private final CraftProjectMapper craftProjectMapper;
    private final CraftRecommendationMapper craftRecommendationMapper;
    private final CraftRecommendationItemMapper craftRecommendationItemMapper;
    private final EmotionSessionMapper emotionSessionMapper;
    private final AiClient aiClient;
    private final com.zhiyu.modules.craft.service.RecommendFeedbackBoost feedbackBoost;

    /** 兜底文案：项目表为空时 */
    private static final String EMPTY_REASON = "暂无符合材料安全范围的推荐项目，请稍后查看或咨询带领者";

    /** 兜底文案：LLM 降级时（AiClient 内部已有，此处用于 checkOutput 的 fallback） */
    private static final String LLM_FALLBACK_REASON = "（暂时无法生成个性化理由，先看看这些手作吧）";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RecommendVO recommend(Long userId, Long sessionId, Integer skillLevel, Integer minutes) {
        // ── 1. 确定 mood ──
        String mood = resolveMood(userId, sessionId);
        log.info("推荐请求: userId={}, sessionId={}, mood={}, skillLevel={}, minutes={}",
                userId, sessionId, mood, skillLevel, minutes);

        // ── 2. 规则层选品 ──
        List<String> preferTags = RecommendRuleConfig.get(mood);
        List<CraftProject> candidates = selectCandidates(preferTags, skillLevel, minutes, userId);

        // ── 3. LLM 生成整体理由 ──
        String reason;
        if (candidates.isEmpty()) {
            reason = EMPTY_REASON;
        } else {
            reason = generateReasonWithSafety(userId, mood, candidates);
        }

        // ── 4. 生成每项理由 ──
        List<RecommendItemVO> itemVOs = buildItemVOs(candidates, preferTags);

        // ── 5. 落库 ──
        CraftRecommendation recommendation = new CraftRecommendation();
        recommendation.setUserId(userId);
        recommendation.setSessionId(sessionId);
        recommendation.setMood(mood);
        recommendation.setReason(reason);
        craftRecommendationMapper.insert(recommendation);
        Long recommendationId = recommendation.getId();

        for (RecommendItemVO item : itemVOs) {
            CraftRecommendationItem entity = new CraftRecommendationItem();
            entity.setRecommendationId(recommendationId);
            entity.setProjectId(item.getProjectId());
            entity.setScore(item.getScore());
            entity.setReason(item.getReason());
            craftRecommendationItemMapper.insert(entity);
        }

        // ── 6. 组装返回 ──
        RecommendVO vo = new RecommendVO();
        vo.setRecommendationId(recommendationId);
        vo.setMood(mood);
        vo.setReason(reason);
        vo.setItems(itemVOs);
        return vo;
    }

    // ──────────────────────────────────────────────────────
    //  内部方法
    // ──────────────────────────────────────────────────────

    /**
     * 确定 mood：
     * <ol>
     *   <li>sessionId 非 null → 从 emotion_session 查 mood</li>
     *   <li>sessionId 为 null → 查该用户最近一条 emotion_session 的 mood</li>
     *   <li>都查不到 → 默认 "anxious"</li>
     * </ol>
     */
    private String resolveMood(Long userId, Long sessionId) {
        if (sessionId != null) {
            EmotionSession session = emotionSessionMapper.selectById(sessionId);
            if (session == null || !java.util.Objects.equals(session.getUserId(), userId)) {
                throw new com.zhiyu.common.exception.BusinessException(1004, "会话不存在");
            }
            if (session.getMood() != null) {
                return session.getMood();
            }
        }
        // 取最近一条会话的 mood
        LambdaQueryWrapper<EmotionSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmotionSession::getUserId, userId)
                .isNotNull(EmotionSession::getMood)
                .orderByDesc(EmotionSession::getCreateTime)
                .last("LIMIT 1");
        EmotionSession latest = emotionSessionMapper.selectOne(wrapper);
        if (latest != null && latest.getMood() != null) {
            return latest.getMood();
        }
        return "anxious";
    }

    /**
     * 规则层选品：过滤 + 打分 + 排序 + 三层兜底。
     *
     * <p>AM2（2026-11-04）：反馈回灌加权接在规则分之后、排序之前
     * （{@link com.zhiyu.modules.craft.service.RecommendFeedbackBoost}）；
     * 开关关闭或反馈不足时 boost 为空表，每个项目 +0.0，排序与原规则逐条一致。
     */
    private List<CraftProject> selectCandidates(List<String> preferTags,
                                                 Integer skillLevel, Integer minutes, Long userId) {
        // 查所有上架项目
        LambdaQueryWrapper<CraftProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CraftProject::getStatus, 1);
        List<CraftProject> allProjects = craftProjectMapper.selectList(wrapper);
        String max=settings.values("recommendation").get("maxSafety").toString();
        Set<Long> safe=new HashSet<>(db.queryForList("SELECT project_id FROM craft_safety WHERE deleted=0 AND (level='LOW' OR (level='GUIDED' AND ?='GUIDED'))",Long.class,max));
        allProjects=allProjects.stream().filter(p->safe.contains(p.getId())).collect(Collectors.toList());

        if (allProjects.isEmpty()) {
            return Collections.emptyList();
        }

        // AM2：本人反馈回灌加权（空表 = 零变化）
        Map<Long, Double> boost = feedbackBoost.boosts(userId);

        // 过滤：level <= skillLevel + 1 且 estMinutes <= minutes
        int maxLevel = skillLevel + 1;
        List<CraftProject> filtered = allProjects.stream()
                .filter(p -> p.getLevel() != null && p.getLevel() <= maxLevel)
                .filter(p -> p.getEstMinutes() != null && p.getEstMinutes() <= minutes)
                .collect(Collectors.toList());

        // 打分 + 排序（规则分 + 反馈增量；增量为 0 时稳定排序保持原序）
        List<ScoredProject> scored = filtered.stream()
                .map(p -> new ScoredProject(p, scoreProject(p, preferTags) + boost.getOrDefault(p.getId(), 0.0)))
                .sorted(Comparator.comparingDouble(ScoredProject::getScore).reversed())
                .collect(Collectors.toList());

        // 兜底 (a)：过滤后为空 → 放松条件重匹配（只按标签，不过滤难度/时长）
        if (scored.isEmpty()) {
            log.info("规则层过滤后为空，放松条件重匹配（只按标签）");
            scored = allProjects.stream()
                    .map(p -> new ScoredProject(p, scoreProject(p, preferTags) + boost.getOrDefault(p.getId(), 0.0)))
                    .sorted(Comparator.comparingDouble(ScoredProject::getScore).reversed())
                    .collect(Collectors.toList());
        }

        // 兜底 (b)：按标签匹配后仍为空（不太可能，allProjects 非空时 scored 非空）→ 返回前 3 条
        if (scored.isEmpty()) {
            log.info("标签匹配后仍为空，返回前 3 条");
            return allProjects.stream().limit((int)settings.number("recommendation","count")).collect(Collectors.toList());
        }

        return scored.stream().limit((int)settings.number("recommendation","count")).map(ScoredProject::getProject).collect(Collectors.toList());
    }

    /**
     * 项目打分：
     * <ul>
     *   <li>therapyTags 命中 preferTags → 每命中一个 +0.6</li>
     *   <li>易上手加分：0.4 * (4 - level) / 3</li>
     * </ul>
     */
    private double scoreProject(CraftProject project, List<String> preferTags) {
        double score = 0.0;
        // 标签命中
        if (project.getTherapyTags() != null && !preferTags.isEmpty()) {
            Set<String> projectTags = new HashSet<>(Arrays.asList(project.getTherapyTags().split(",")));
            for (String tag : preferTags) {
                if (projectTags.contains(tag.trim())) {
                    score += settings.number("recommendation","tagWeight");
                }
            }
        }
        // 易上手加分
        if (project.getLevel() != null) {
            score += settings.number("recommendation","easeWeight") * (4 - project.getLevel()) / 3.0;
        }
        return score;
    }

    /**
     * LLM 生成整体理由。
     *
     * <p>AiClient 内部走熔断（key=llm-recommend）+ 输出安全过滤，
     * 失败返回降级文案，越界自动改写。本方法不再显式调 checkOutput。
     */
    private String generateReasonWithSafety(Long userId, String mood, List<CraftProject> candidates) {
        List<String> craftNames = candidates.stream()
                .map(CraftProject::getTitle)
                .collect(Collectors.toList());

        return aiClient.recommendReason(mood, craftNames);
    }

    /**
     * 构建推荐明细 VO 列表（含每项理由）。
     *
     * <p>每项理由：从项目 therapy_tags 取与 preferTags 交集最多的 2 个标签，
     * 模板「{title} 适合做 {标签列表} 的练习」，不超过 50 字。
     */
    private List<RecommendItemVO> buildItemVOs(List<CraftProject> candidates, List<String> preferTags) {
        List<RecommendItemVO> result = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            CraftProject p = candidates.get(i);
            RecommendItemVO vo = new RecommendItemVO();
            vo.setProjectId(p.getId());
            vo.setTitle(p.getTitle());
            vo.setIntro(p.getIntro());
            vo.setCultureBg(p.getCultureBg());
            vo.setLevel(p.getLevel());
            vo.setEstMinutes(p.getEstMinutes());
            vo.setTherapyTags(p.getTherapyTags());
            vo.setCoverUrl(p.getCoverUrl());
            // score：按排名递减（3, 2, 1）
            vo.setScore((double) (candidates.size() - i));
            vo.setReason(buildItemReason(p, preferTags));
            result.add(vo);
        }
        return result;
    }

    /**
     * 生成单项理由。
     */
    private String buildItemReason(CraftProject project, List<String> preferTags) {
        // 从项目 therapy_tags 取与 preferTags 交集最多的 2 个标签
        List<String> matchedTags = new ArrayList<>();
        if (project.getTherapyTags() != null) {
            Set<String> projectTags = new HashSet<>(Arrays.asList(project.getTherapyTags().split(",")));
            projectTags.forEach(t -> t = t.trim());
            for (String tag : preferTags) {
                if (projectTags.contains(tag)) {
                    matchedTags.add(tag);
                }
                if (matchedTags.size() >= 2) {
                    break;
                }
            }
        }

        String tagList = matchedTags.isEmpty()
                ? (project.getTherapyTags() != null ? project.getTherapyTags() : "手作")
                : String.join("、", matchedTags);

        String reason = project.getTitle() + " 适合做 " + tagList + " 的练习";
        // 不超过 50 字
        if (reason.length() > 50) {
            reason = reason.substring(0, 50);
        }
        return reason;
    }

    /**
     * 打分中间对象。
     */
    private static class ScoredProject {
        private final CraftProject project;
        private final double score;

        ScoredProject(CraftProject project, double score) {
            this.project = project;
            this.score = score;
        }

        CraftProject getProject() {
            return project;
        }

        double getScore() {
            return score;
        }
    }
}
