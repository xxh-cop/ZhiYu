package com.zhiyu.modules.craft.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.craft.dto.RecommendClickDTO;
import com.zhiyu.modules.craft.entity.CraftRecommendEvent;
import com.zhiyu.modules.craft.entity.CraftRecommendation;
import com.zhiyu.modules.craft.entity.CraftRecommendationItem;
import com.zhiyu.modules.craft.mapper.CraftRecommendEventMapper;
import com.zhiyu.modules.craft.mapper.CraftRecommendationItemMapper;
import com.zhiyu.modules.craft.mapper.CraftRecommendationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 推荐点击事件服务（20261028）。
 *
 * <p>点击必须属于当前用户的推荐与推荐中的项目；重复点击幂等成功。
 * 失败不抛系统异常，学生端会静默忽略。
 */
@Service
@RequiredArgsConstructor
public class RecommendEventService {

    private static final String CLICK = "CLICK";

    private final CraftRecommendationMapper recommendationMapper;
    private final CraftRecommendationItemMapper itemMapper;
    private final CraftRecommendEventMapper eventMapper;

    @Transactional
    public void click(Long userId, RecommendClickDTO dto) {
        if (userId == null) {
            throw new BusinessException(1002, "请先登录");
        }
        Long recommendationId = parseId(dto.getRecommendationId(), "推荐编号格式不正确");
        Long projectId = parseId(dto.getProjectId(), "项目编号格式不正确");
        if (!"HOME".equals(dto.getSource()) && !"RECOMMEND".equals(dto.getSource())) {
            throw new BusinessException(1001, "来源只能是 HOME 或 RECOMMEND");
        }

        CraftRecommendation recommendation = recommendationMapper.selectById(recommendationId);
        if (recommendation == null || !Objects.equals(recommendation.getUserId(), userId)) {
            throw new BusinessException(1004, "推荐记录不存在");
        }

        Long itemCount = itemMapper.selectCount(new LambdaQueryWrapper<CraftRecommendationItem>()
                .eq(CraftRecommendationItem::getRecommendationId, recommendationId)
                .eq(CraftRecommendationItem::getProjectId, projectId));
        if (itemCount == null || itemCount == 0) {
            throw new BusinessException(1004, "推荐项目不存在");
        }

        Long existing = eventMapper.selectCount(new LambdaQueryWrapper<CraftRecommendEvent>()
                .eq(CraftRecommendEvent::getUserId, userId)
                .eq(CraftRecommendEvent::getRecommendationId, recommendationId)
                .eq(CraftRecommendEvent::getProjectId, projectId)
                .eq(CraftRecommendEvent::getEventType, CLICK));
        if (existing != null && existing > 0) {
            return;
        }

        CraftRecommendEvent event = new CraftRecommendEvent();
        event.setUserId(userId);
        event.setRecommendationId(recommendationId);
        event.setProjectId(projectId);
        event.setEventType(CLICK);
        event.setSource(dto.getSource());
        try {
            eventMapper.insert(event);
        } catch (DuplicateKeyException ignored) {
            // 并发重复点击由唯一键兜底，幂等返回成功。
        }
    }

    private Long parseId(String value, String message) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(1001, message);
        }
    }
}
