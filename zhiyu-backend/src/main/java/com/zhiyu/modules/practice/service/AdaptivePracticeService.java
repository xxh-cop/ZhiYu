package com.zhiyu.modules.practice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.companion.memory.CompanionMemoryService;
import com.zhiyu.modules.heritage.model.HeritageCatalog;
import com.zhiyu.modules.heritage.service.HeritageCatalogService;
import com.zhiyu.modules.practice.entity.PracticeSession;
import com.zhiyu.modules.practice.enums.PracticeSessionStatus;
import com.zhiyu.modules.practice.mapper.PracticeSessionMapper;
import com.zhiyu.modules.practice.vo.AdaptivePlanVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 自适应练习计划：只根据时间预算、最近完成/放弃记录和已授权偏好调整节奏。
 * 不读取情绪原文，不根据情绪推断疗效，也不承诺改善。
 */
@Service
public class AdaptivePracticeService {

    private static final Map<String, String> FAMILY_LABELS = Map.of(
            "stitch", "针线",
            "weave", "编织",
            "cutout", "剪刻",
            "porcelain", "器面");

    private final PracticeSessionMapper sessions;
    private final CompanionMemoryService memory;
    private final HeritageCatalogService catalogs;

    public AdaptivePracticeService(PracticeSessionMapper sessions, CompanionMemoryService memory,
                                   HeritageCatalogService catalogs) {
        this.sessions = sessions;
        this.memory = memory;
        this.catalogs = catalogs;
    }

    public AdaptivePlanVO plan(Long userId, Integer requestedMinutes) {
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        Map<String, String> preferences = memory.preferences(userId);
        int minutes;
        String source;
        if (requestedMinutes != null) {
            if (requestedMinutes < 1 || requestedMinutes > 180) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "练习时间需在 1 到 180 分钟之间");
            }
            minutes = requestedMinutes;
            source = "MANUAL";
        } else if (memoryMinutes(preferences) > 0) {
            minutes = memoryMinutes(preferences);
            source = "MEMORY";
        } else {
            minutes = 15;
            source = "DEFAULT";
        }

        List<PracticeSession> recent = recent(userId);
        int completed = 0;
        int abandoned = 0;
        for (PracticeSession session : recent) {
            int status = session.getStatus() == null ? -1 : session.getStatus();
            if (status == PracticeSessionStatus.COMPLETED.getCode()
                    || status == PracticeSessionStatus.ARTIFACT_SAVED.getCode()) completed++;
            if (status == PracticeSessionStatus.ABANDONED.getCode()) abandoned++;
        }
        int decided = completed + abandoned;
        double rate = decided == 0 ? 0 : (double) completed / decided;
        String levelBias = completed >= 2 && rate >= 0.7 ? "进阶" : "入门";
        String family = preferences.getOrDefault(CompanionMemoryService.KEY_FAMILY, "");

        List<AdaptivePlanVO.Item> items = pickCatalog(minutes, levelBias, family);
        boolean historyUsed = decided > 0;
        String reason = reason(completed, abandoned, minutes, levelBias, family, historyUsed);
        return new AdaptivePlanVO(minutes, levelBias, rate, completed, abandoned,
                source, historyUsed, false, reason, items);
    }

    private List<PracticeSession> recent(Long userId) {
        LambdaQueryWrapper<PracticeSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PracticeSession::getUserId, userId)
                .orderByDesc(PracticeSession::getCreateTime)
                .last("LIMIT 10");
        List<PracticeSession> rows = sessions.selectList(wrapper);
        return rows == null ? List.of() : rows;
    }

    private int memoryMinutes(Map<String, String> preferences) {
        String value = preferences.get(CompanionMemoryService.KEY_MINUTES);
        if (value == null) return 0;
        try {
            int minutes = Integer.parseInt(value);
            return minutes >= 1 && minutes <= 180 ? minutes : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private List<AdaptivePlanVO.Item> pickCatalog(int minutes, String levelBias, String family) {
        HeritageCatalog catalog = catalogs.catalog();
        Map<String, String> traditions = new java.util.LinkedHashMap<>();
        for (HeritageCatalog.Tradition tradition : catalog.traditions()) {
            traditions.put(tradition.id(), tradition.name());
        }
        List<HeritageCatalog.Pattern> sorted = new ArrayList<>(catalog.patterns());
        int fitMinutes = Math.max(minutes, 8);
        sorted.sort(Comparator
                .comparingInt((HeritageCatalog.Pattern pattern) -> levelRank(pattern.level(), levelBias))
                .thenComparingInt(pattern -> Math.abs(pattern.minutes() - minutes))
                .thenComparingInt(pattern -> Objects.equals(family, pattern.family()) ? 0 : 1)
                .thenComparing(HeritageCatalog.Pattern::id));
        List<HeritageCatalog.Pattern> picked = sorted.stream()
                .filter(pattern -> pattern.minutes() <= fitMinutes)
                .limit(2)
                .toList();
        if (picked.isEmpty()) picked = sorted.stream().limit(2).toList();
        return picked.stream().map(pattern -> new AdaptivePlanVO.Item(
                pattern.id(),
                pattern.name(),
                traditions.getOrDefault(pattern.traditionId(), pattern.traditionId()),
                pattern.family(),
                FAMILY_LABELS.getOrDefault(pattern.family(), pattern.family()),
                pattern.minutes(),
                pattern.level(),
                pattern.learningFocus(),
                pattern.palette(),
                pattern.minutes() <= minutes
                        ? pattern.minutes() + " 分钟 · " + pattern.level() + " · 适合当前时间预算。"
                        : pattern.minutes() + " 分钟 · " + pattern.level() + " · 比预算稍长，可以分两次完成。"
        )).toList();
    }

    private int levelRank(String level, String bias) {
        if (Objects.equals(level, bias)) return 0;
        if (Objects.equals(level, "入门")) return 1;
        return 2;
    }

    private String reason(int completed, int abandoned, int minutes,
                          String levelBias, String family, boolean historyUsed) {
        StringBuilder text = new StringBuilder();
        if (historyUsed) {
            text.append("按你最近的练习记录（完成 ").append(completed)
                    .append(" 次、放弃 ").append(abandoned).append(" 次）");
        } else {
            text.append("你还没有练习记录");
        }
        text.append("和 ").append(minutes).append(" 分钟预算，先安排").append(levelBias).append("节奏。");
        if (family != null && !family.isBlank()) {
            text.append("优先看看你偏好的").append(FAMILY_LABELS.getOrDefault(family, family)).append("纹样。");
        }
        text.append("只调整练习节奏，不作情绪或疗效判断。");
        return text.toString();
    }
}
