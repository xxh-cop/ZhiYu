package com.zhiyu.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.common.dto.PageVO;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.admin.dto.*;
import com.zhiyu.modules.admin.service.AdminService;
import com.zhiyu.modules.audit.entity.CareReminder;
import com.zhiyu.modules.audit.mapper.CareReminderMapper;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.emotion.entity.EmotionSession;
import com.zhiyu.modules.emotion.mapper.EmotionSessionMapper;
import com.zhiyu.modules.work.entity.Work;
import com.zhiyu.modules.work.mapper.WorkMapper;
import com.zhiyu.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端服务实现（M2 批 4）。
 *
 * <p>复用现有 Mapper（CraftProjectMapper / CareReminderMapper / UserMapper /
 * WorkMapper / EmotionSessionMapper），不新建 Mapper。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private static final int PAGE_SIZE = 10;

    private final CraftProjectMapper craftProjectMapper;
    private final CareReminderMapper careReminderMapper;
    private final UserMapper userMapper;
    private final WorkMapper workMapper;
    private final EmotionSessionMapper emotionSessionMapper;

    @Override
    public PageVO<AuditQueueItemVO> getAuditQueue(String type, Integer page) {
        // 第一版只支持 project 类型
        if (!"project".equals(type)) {
            return new PageVO<>(0, 1, PAGE_SIZE, Collections.emptyList());
        }

        int pageNum = (page == null || page < 1) ? 1 : page;
        Page<CraftProject> pageParam = new Page<>(pageNum, PAGE_SIZE);
        LambdaQueryWrapper<CraftProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CraftProject::getStatus, 0)
                .orderByDesc(CraftProject::getCreateTime);

        IPage<CraftProject> result = craftProjectMapper.selectPage(pageParam, wrapper);

        List<AuditQueueItemVO> records = result.getRecords().stream()
                .map(this::toAuditQueueItemVO)
                .collect(Collectors.toList());
        return new PageVO<>(result.getTotal(), pageNum, PAGE_SIZE, records);
    }

    @Override
    public AuditQueueItemVO auditProject(Long projectId, AuditRequest req) {
        CraftProject project = craftProjectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (project.getStatus() != null && project.getStatus() != 0) {
            throw new BusinessException(ResultCode.BAD_STATE);
        }

        String action = req.getAction();
        if ("approve".equals(action)) {
            project.setStatus(1);
        } else if ("reject".equals(action)) {
            if (req.getReason() == null || req.getReason().isBlank()) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "驳回必须填写理由");
            }
            project.setStatus(2);
        } else {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "action 只能为 approve 或 reject");
        }

        craftProjectMapper.updateById(project);
        log.info("项目审核完成: projectId={}, action={}, operator={}",
                projectId, action, UserContext.getUserId());

        return toAuditQueueItemVO(project);
    }

    @Override
    public PageVO<CareReminderVO> getCareReminders(Integer status, Integer page) {
        int pageNum = (page == null || page < 1) ? 1 : page;
        Page<CareReminder> pageParam = new Page<>(pageNum, PAGE_SIZE);
        LambdaQueryWrapper<CareReminder> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(CareReminder::getStatus, status);
        }
        wrapper.orderByDesc(CareReminder::getCreateTime);

        IPage<CareReminder> result = careReminderMapper.selectPage(pageParam, wrapper);

        List<CareReminderVO> records = result.getRecords().stream()
                .map(this::toCareReminderVO)
                .collect(Collectors.toList());
        return new PageVO<>(result.getTotal(), pageNum, PAGE_SIZE, records);
    }

    @Override
    public DashboardStatsVO getDashboardStats() {
        DashboardStatsVO vo = new DashboardStatsVO();

        // totalUsers
        long totalUsers = userMapper.selectCount(null);
        vo.setTotalUsers(totalUsers);

        // totalWorks
        long totalWorks = workMapper.selectCount(null);
        vo.setTotalWorks(totalWorks);

        // pendingProjects
        LambdaQueryWrapper<CraftProject> projectWrapper = new LambdaQueryWrapper<>();
        projectWrapper.eq(CraftProject::getStatus, 0);
        long pendingProjects = craftProjectMapper.selectCount(projectWrapper);
        vo.setPendingProjects(pendingProjects);

        // pendingCareReminders
        LambdaQueryWrapper<CareReminder> reminderWrapper = new LambdaQueryWrapper<>();
        reminderWrapper.eq(CareReminder::getStatus, 0);
        long pendingCareReminders = careReminderMapper.selectCount(reminderWrapper);
        vo.setPendingCareReminders(pendingCareReminders);

        // moodDistribution
        vo.setMoodDistribution(getMoodDistribution());

        // userTrend7d
        vo.setUserTrend7d(getUserTrend7d());

        return vo;
    }

    // ── 私有方法 ──

    private AuditQueueItemVO toAuditQueueItemVO(CraftProject project) {
        AuditQueueItemVO vo = new AuditQueueItemVO();
        vo.setId(project.getId());
        vo.setTitle(project.getTitle());
        vo.setIntro(project.getIntro());
        vo.setCategory(project.getCategory());
        vo.setLevel(project.getLevel());
        vo.setEstMinutes(project.getEstMinutes());
        vo.setTherapyTags(project.getTherapyTags());
        vo.setCoverUrl(project.getCoverUrl());
        vo.setVideoUrl(project.getVideoUrl());
        vo.setInheritorId(project.getInheritorId());
        vo.setStatus(project.getStatus());
        vo.setCreateTime(project.getCreateTime());
        return vo;
    }

    private CareReminderVO toCareReminderVO(CareReminder reminder) {
        CareReminderVO vo = new CareReminderVO();
        vo.setId(reminder.getId());
        vo.setUserId(reminder.getUserId());
        vo.setSessionId(reminder.getSessionId());
        vo.setRiskLevel(reminder.getRiskLevel());
        vo.setSummary(reminder.getSummary());
        vo.setStatus(reminder.getStatus());
        vo.setCreateTime(reminder.getCreateTime());
        return vo;
    }

    private List<DashboardStatsVO.MoodItem> getMoodDistribution() {
        List<EmotionSession> sessions = emotionSessionMapper.selectList(null);
        Map<String, Long> moodCount = sessions.stream()
                .filter(s -> s.getMood() != null && !s.getMood().isBlank())
                .collect(Collectors.groupingBy(EmotionSession::getMood, Collectors.counting()));

        return moodCount.entrySet().stream()
                .map(e -> {
                    DashboardStatsVO.MoodItem item = new DashboardStatsVO.MoodItem();
                    item.setMood(e.getKey());
                    item.setCount(e.getValue().intValue());
                    return item;
                })
                .sorted(Comparator.comparing(DashboardStatsVO.MoodItem::getCount).reversed())
                .collect(Collectors.toList());
    }

    private List<DashboardStatsVO.TrendItem> getUserTrend7d() {
        LocalDateTime sevenDaysAgo = LocalDate.now().minusDays(6).atStartOfDay();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(User::getCreateTime, sevenDaysAgo);
        List<User> recentUsers = userMapper.selectList(wrapper);

        Map<LocalDate, Long> dailyCount = recentUsers.stream()
                .filter(u -> u.getCreateTime() != null)
                .collect(Collectors.groupingBy(
                        u -> u.getCreateTime().toLocalDate(),
                        Collectors.counting()));

        List<DashboardStatsVO.TrendItem> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            DashboardStatsVO.TrendItem item = new DashboardStatsVO.TrendItem();
            item.setDate(date.toString());
            item.setCount(dailyCount.getOrDefault(date, 0L).intValue());
            trend.add(item);
        }
        return trend;
    }
}
