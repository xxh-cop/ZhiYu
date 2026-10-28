package com.zhiyu.modules.admin.service;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.admin.vo.TherapyFunnelVO;
import com.zhiyu.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 疗愈闭环漏斗聚合（20261028）。
 *
 * <p>口径：同人群、同时间窗、按 user_id 去重；不强制 session_id 串联，
 * 因为首页推荐可以不带 sessionId。它是运营漏斗，不是因果归因。
 */
@Service
@RequiredArgsConstructor
public class TherapyFunnelService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MIN_COHORT = 5;

    private final JdbcTemplate db;

    @Transactional(readOnly = true)
    public TherapyFunnelVO funnel(int days) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(1002, "请先登录");
        }
        Integer admin = db.queryForObject(
                "SELECT COUNT(*) FROM user WHERE id=? AND role=3 AND status=1 AND deleted=0",
                Integer.class, userId);
        if (admin == null || admin != 1) {
            throw new BusinessException(1003, "仅管理员可查看疗愈漏斗");
        }
        if (days != 7 && days != 30) {
            throw new BusinessException(1001, "请选择7天或30天");
        }

        LocalDate today = LocalDate.now(ZONE);
        LocalDateTime start = today.minusDays(days - 1).atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        Set<Long> cohort = queryUsers("""
                SELECT DISTINCT e.user_id
                FROM emotion_session e
                JOIN user u ON u.id=e.user_id AND u.role=0 AND u.status=1 AND u.deleted=0
                WHERE e.deleted=0 AND e.create_time>=? AND e.create_time<?
                """, start, end);

        Set<Long> recommended = queryUsers(
                "SELECT DISTINCT user_id FROM craft_recommendation WHERE deleted=0 AND create_time>=? AND create_time<?",
                start, end);
        recommended.retainAll(cohort);

        Set<Long> clicked = queryUsers(
                "SELECT DISTINCT user_id FROM craft_recommend_event WHERE deleted=0 AND event_type='CLICK' AND create_time>=? AND create_time<?",
                start, end);
        clicked.retainAll(cohort);

        Set<Long> started = queryUsers(
                "SELECT DISTINCT user_id FROM craft_session WHERE deleted=0 AND status IN (1,2,3,4) AND start_time>=? AND start_time<?",
                start, end);
        started.retainAll(cohort);

        Set<Long> completed = queryUsers(
                "SELECT DISTINCT user_id FROM craft_session WHERE deleted=0 AND status=3 AND end_time>=? AND end_time<?",
                start, end);
        completed.retainAll(cohort);

        Set<Long> published = queryUsers(
                "SELECT DISTINCT user_id FROM work WHERE deleted=0 AND status IN (0,1) AND create_time>=? AND create_time<?",
                start, end);
        published.retainAll(cohort);

        boolean insufficient = cohort.size() < MIN_COHORT;
        List<TherapyFunnelVO.Stage> stages = new ArrayList<>();
        int previous = cohort.size();
        stages.add(stage("emotion", "情绪会话", cohort.size(), null, rate(cohort.size(), cohort.size()), insufficient));
        stages.add(stage("recommended", "获得推荐", recommended.size(), rate(recommended.size(), previous), rate(recommended.size(), cohort.size()), insufficient));
        previous = recommended.size();
        stages.add(stage("clicked", "点击推荐", clicked.size(), rate(clicked.size(), previous), rate(clicked.size(), cohort.size()), insufficient));
        previous = clicked.size();
        stages.add(stage("started", "开始创作", started.size(), rate(started.size(), previous), rate(started.size(), cohort.size()), insufficient));
        previous = started.size();
        stages.add(stage("completed", "完成创作", completed.size(), rate(completed.size(), previous), rate(completed.size(), cohort.size()), insufficient));
        previous = completed.size();
        stages.add(stage("published", "提交作品", published.size(), rate(published.size(), previous), rate(published.size(), cohort.size()), insufficient));

        TherapyFunnelVO.Flow flow = flow(start, end, cohort);
        return new TherapyFunnelVO(days, cohort.size(), insufficient, stages, flow,
                ZonedDateTime.now(ZONE).toOffsetDateTime().toString());
    }

    private TherapyFunnelVO.Stage stage(String key, String label, int users,
                                        Double fromPrevious, Double fromStart, boolean insufficient) {
        if (insufficient) {
            return new TherapyFunnelVO.Stage(key, label, users, null, null);
        }
        return new TherapyFunnelVO.Stage(key, label, users, fromPrevious, fromStart);
    }

    private Double rate(int numerator, int denominator) {
        return denominator <= 0 ? null : (double) numerator / denominator;
    }

    private TherapyFunnelVO.Flow flow(LocalDateTime start, LocalDateTime end, Set<Long> cohort) {
        int flowSeconds = 0;
        long totalSeconds = 0;
        List<Timestamp[]> times = new ArrayList<>();
        List<Integer> flows = new ArrayList<>();
        db.query("""
                SELECT user_id, flow_seconds, start_time, end_time
                FROM craft_session
                WHERE deleted=0 AND status=3 AND end_time>=? AND end_time<?
                """, (RowCallbackHandler) rs -> {
                    long uid = rs.getLong("user_id");
                    if (!cohort.contains(uid)) {
                        return;
                    }
                    flows.add(rs.getInt("flow_seconds"));
                    times.add(new Timestamp[]{rs.getTimestamp("start_time"), rs.getTimestamp("end_time")});
                }, start, end);
        int sessions = times.size();
        for (int i = 0; i < times.size(); i++) {
            flowSeconds += flows.get(i);
            Timestamp begin = times.get(i)[0];
            Timestamp finish = times.get(i)[1];
            if (begin != null && finish != null) {
                long seconds = Duration.between(begin.toLocalDateTime(), finish.toLocalDateTime()).getSeconds();
                if (seconds > 0) {
                    totalSeconds += seconds;
                }
            }
        }
        Double focusRate = totalSeconds > 0 ? (double) flowSeconds / totalSeconds : null;
        return new TherapyFunnelVO.Flow(sessions, flowSeconds, focusRate);
    }

    private Set<Long> queryUsers(String sql, Object... args) {
        Set<Long> users = new HashSet<>();
        db.query(sql, (RowCallbackHandler) rs -> users.add(rs.getLong(1)), args);
        return users;
    }
}
