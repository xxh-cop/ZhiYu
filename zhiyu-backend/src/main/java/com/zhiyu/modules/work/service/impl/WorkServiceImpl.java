package com.zhiyu.modules.work.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.entity.CraftSession;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.craft.mapper.CraftSessionMapper;
import com.zhiyu.ai.AiClient;
import com.zhiyu.modules.work.dto.WorkPublishReq;
import com.zhiyu.modules.work.entity.Work;
import com.zhiyu.modules.work.entity.WorkComment;
import com.zhiyu.modules.work.mapper.WorkCommentMapper;
import com.zhiyu.modules.work.mapper.WorkMapper;
import com.zhiyu.modules.work.service.WorkService;
import com.zhiyu.modules.work.vo.WorkPageVO;
import com.zhiyu.modules.work.vo.WorkVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 作品服务实现（T1.8a）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkServiceImpl implements WorkService {

    private final com.zhiyu.modules.work.service.WorkAudience audience;
    private final org.springframework.jdbc.core.JdbcTemplate db;
    private final com.zhiyu.ai.safety.ContentSafetyFilter safety;
    private final WorkMapper workMapper;
    private final WorkCommentMapper workCommentMapper;
    private final UserMapper userMapper;
    private final CraftSessionMapper craftSessionMapper;
    private final CraftProjectMapper craftProjectMapper;
    private final AiClient aiClient;
    private final com.zhiyu.modules.file.MediaService media;
    private final com.zhiyu.modules.work.mapper.WorkLikeMapper workLikeMapper;

    private static final String ANONYMOUS_NAME = "匿名同学";

    /**
     * 软档处置链读侧开关（2026-11-07）：关闭 = 社区流纯时间序（回滚点，完全忽略 soft_demote_until）；
     * 开启 = 位次降权版查询（WorkMapper.selectFeedPage）。
     */
    @org.springframework.beans.factory.annotation.Value("${zhiyu.moderation.soft-tier.disposition.enabled:false}")
    private boolean softDispositionEnabled;
    @org.springframework.beans.factory.annotation.Value("${zhiyu.moderation.soft-tier.disposition.demote-position:20}")
    private int softDemotePosition;

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public WorkVO publish(Long userId, WorkPublishReq req) {
        if(req.getVisible()!=null && !List.of(0,1,2).contains(req.getVisible()))throw new BusinessException(1001,"请选择有效的可见范围");
        if(Objects.equals(req.getVisible(),2))audience.member(req.getGroupId(),userId);
        if(safety.checkInput(userId,null,"work",req.getTitle()+" "+Objects.toString(req.getMessage(),"")).getAction()==com.zhiyu.ai.safety.SafetyAction.BLOCK)throw new BusinessException(1001,"内容暂不适合发布，请调整后重试");
        if(req.getCoverUrl()!=null&&req.getCoverUrl().startsWith("/api/media/"))media.requireOwned(userId,req.getCoverUrl());
        else if(Objects.equals(req.getVisible(),2)&&req.getCoverUrl()!=null&&!req.getCoverUrl().isBlank())throw new BusinessException(1001,"小组作品请重新上传受保护的图片");
        // ── 媒体类型校验（批次 2） ──
        String mediaType = (req.getMediaType() == null || req.getMediaType().isBlank())
                ? "image" : req.getMediaType().toLowerCase().trim();

        switch (mediaType) {
            case "image" -> {
                // image 类型：coverUrl 必填，videoUrl 禁用
                if (req.getCoverUrl() == null || req.getCoverUrl().isBlank()) {
                    throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                            "图片类型作品必须上传封面图");
                }
            }
            case "video" -> {
                // video 类型：videoUrl 必填
                if (req.getVideoUrl() == null || req.getVideoUrl().isBlank()) {
                    throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                            "视频类型作品必须上传视频");
                }
            }
            case "text" -> {
                // text 类型：coverUrl 和 videoUrl 都允许空
            }
            default -> throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "不支持的媒体类型：" + mediaType + "，允许：image/video/text");
        }

        Work work = new Work();
        work.setUserId(userId);
        work.setTitle(req.getTitle());
        work.setVisible(req.getVisible() != null ? req.getVisible() : 1);
        work.setAnonymous(Boolean.FALSE.equals(req.getAnonymous()) ? 0 : 1);
        work.setLikeCount(0);
        // N1（2026-11-04 过渡方案）：先审后发——text/image/video 一律 status=0 待审，
        // 仅作者本人可见，管理员审核通过（status=1）后转公开；历史已公开内容不回滚。
        work.setStatus(0);
        work.setMediaType(mediaType);

        // 设置封面图和视频 URL
        if ("image".equals(mediaType)) {
            work.setCoverUrl(req.getCoverUrl());
        } else if ("video".equals(mediaType)) {
            work.setVideoUrl(media.requireOwned(userId,req.getVideoUrl()));
            // video 类型也可以有封面图（可选）
            if (req.getCoverUrl() != null && !req.getCoverUrl().isBlank()) {
                work.setCoverUrl(req.getCoverUrl());
            }
        }
        // text 类型：coverUrl 和 videoUrl 都为 null

        // 如果 sessionId 非空，继承 coverUrl（仅当 coverUrl 还未设置时）
        if (req.getSessionId() != null) {
            CraftSession session = craftSessionMapper.selectById(req.getSessionId());
            if (session == null) {
                throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
            }
            if (!session.getUserId().equals(userId)) {
                throw new BusinessException(ResultCode.FORBIDDEN);
            }
            work.setSessionId(req.getSessionId());
            work.setProjectId(session.getProjectId());

            // 从 craft_project 继承 coverUrl（仅当未手动设置时）
            if (work.getCoverUrl() == null) {
                CraftProject project = craftProjectMapper.selectById(session.getProjectId());
                if (project != null) {
                    work.setCoverUrl(project.getCoverUrl());
                }
            }
        }

        // message：前端传了就用前端的；否则调 AiClient 生成（内部含输出安全过滤）
        if (req.getMessage() != null && !req.getMessage().isBlank()) {
            work.setMessage(req.getMessage());
        } else {
            // craft_session.emotion_session_id 已补（P0 修复），
            // 但通过 session → emotion_session 查 mood 的链路逻辑待 M2 实现，
            // 当前传空字符串，Prompt 模板对空 mood 有兜底。
            String mood = "";
            if(req.getSessionId()!=null){var moods=db.queryForList("SELECT e.mood FROM craft_session c JOIN emotion_session e ON e.id=c.emotion_session_id AND e.user_id=c.user_id AND e.deleted=0 WHERE c.id=? AND c.user_id=? AND c.deleted=0",String.class,req.getSessionId(),userId);if(!moods.isEmpty())mood=Objects.toString(moods.get(0),"");}
            String generated = aiClient.workMessage(req.getTitle(), mood);
            work.setMessage(generated);
            log.info("AI 生成寄语: workId(预)={}, title={}, message={}", work.getId(), req.getTitle(), generated);
        }

        workMapper.insert(work);
        if(Objects.equals(work.getVisible(),2))audience.attach(work.getId(),req.getGroupId());
        log.info("发布作品: workId={}, userId={}, title={}, mediaType={}", work.getId(), userId, req.getTitle(), mediaType);

        return buildVO(work, getNickname(userId));
    }

    @Override
    public WorkPageVO listWorks(Integer page, Integer size) {
        int pageNum = (page == null || page < 1) ? 1 : page;
        int pageSize = (size == null || size < 1) ? 10 : Math.min(size, 50);

        // 分页查 work where status=1 AND visible=1 AND deleted=0
        // 处置链开启时走位次降权版（软档内容落在第 N 条非软档之后）；关闭保持纯时间序（回滚点）
        IPage<Work> pageResult;
        if (softDispositionEnabled) {
            // Page 第三参 false：不自动 count（自动 count 会把 ORDER BY 包进聚合子查询，H2 拒绝）；
            // total 由独立计数回填，口径与排序无关
            Page<Work> feedPage = new Page<>(pageNum, pageSize, false);
            pageResult = workMapper.selectFeedPage(feedPage, softDemotePosition);
            pageResult.setTotal(workMapper.selectFeedCount());
        } else {
            LambdaQueryWrapper<Work> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Work::getStatus, 1)
                    .eq(Work::getVisible, 1)
                    .orderByDesc(Work::getCreateTime);
            pageResult = workMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        }

        // 批量查 user 昵称
        Set<Long> userIds = pageResult.getRecords().stream()
                .map(Work::getUserId)
                .collect(Collectors.toSet());
        Map<Long, String> nicknameMap = batchGetNicknames(userIds);

        // 批量查 commentCount
        Map<Long, Integer> commentCountMap = batchGetCommentCounts(
                pageResult.getRecords().stream().map(Work::getId).collect(Collectors.toSet()));

        List<WorkVO> voList = pageResult.getRecords().stream()
                .map(w -> {
                    WorkVO vo = buildVO(w, nicknameMap.get(w.getUserId()));
                    vo.setCommentCount(commentCountMap.getOrDefault(w.getId(), 0));
                    return vo;
                })
                .collect(Collectors.toList());

        WorkPageVO result = new WorkPageVO();
        result.setTotal(Math.toIntExact(pageResult.getTotal())); // AE3：JSON number 口径
        result.setPage(pageNum);
        result.setSize(pageSize);
        result.setRecords(voList);
        return result;
    }

    // ── T1.9c-backend 作品详情 ──

    @Override
    public WorkVO getWork(Long userId, Long workId) {
        // 1. 查作品：status≠1（待审/下架）仅作者本人可见；他人 1004（不泄露存在性）
        Work work = workMapper.selectById(workId);
        if (work == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        boolean isAuthor = Objects.equals(work.getUserId(), userId);
        if (!Objects.equals(work.getStatus(), 1) && !isAuthor) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        // 2. 权限校验：visible=0（私密）只有作者能看
        if (!audience.canRead(work, userId) && !isAuthor) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        // 历史小组可见标记在成员授权接入前仅允许作者访问。

        // 3. 组装 VO
        String nickname = getNickname(work.getUserId());
        WorkVO vo = buildVO(work, nickname);
        // AE2（2026-11-04）：审核理由仅作者本人可见；他人不下发（未审核/已通过天然为 null）
        vo.setAuditReason(isAuthor ? work.getAuditReason() : null);
        // 软档降权状态（2026-11-07 处置链）：三条件同时成立才下发——处置链开启 + 作者本人 + 降权未过期
        // （开关关闭或已过期时徽标必须消失，否则是「告诉你受限但其实没有」的反向静默）
        vo.setSoftDemoteUntil(isAuthor && softDispositionEnabled
                && work.getSoftDemoteUntil() != null && work.getSoftDemoteUntil().isAfter(LocalDateTime.now())
                ? work.getSoftDemoteUntil() : null);

        // 4. 查 commentCount
        LambdaQueryWrapper<WorkComment> commentWrapper = new LambdaQueryWrapper<>();
        commentWrapper.eq(WorkComment::getWorkId, workId)
                .eq(WorkComment::getStatus, 1);
        Long count = workCommentMapper.selectCount(commentWrapper);
        vo.setCommentCount(count != null ? count.intValue() : 0);
        var interactions = workLikeMapper.selectList(new LambdaQueryWrapper<com.zhiyu.modules.work.entity.WorkLike>()
                .eq(com.zhiyu.modules.work.entity.WorkLike::getWorkId, workId)
                .eq(com.zhiyu.modules.work.entity.WorkLike::getUserId, userId));
        vo.setLiked(interactions.stream().anyMatch(i -> Objects.equals(i.getType(), 1)));
        vo.setFavorited(interactions.stream().anyMatch(i -> Objects.equals(i.getType(), 0)));

        return vo;
    }

    // ──────────────────────────────────────────────────────
    //  内部方法
    // ──────────────────────────────────────────────────────

    private String getNickname(Long userId) {
        User user = userMapper.selectById(userId);
        return user != null ? user.getNickname() : "未知用户";
    }

    private Map<Long, String> batchGetNicknames(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        return users.stream().collect(Collectors.toMap(User::getId, User::getNickname));
    }

    private Map<Long, Integer> batchGetCommentCounts(Set<Long> workIds) {
        if (workIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 逐个 count（workIds 通常很小，一页最多 50）
        Map<Long, Integer> result = new HashMap<>();
        for (Long workId : workIds) {
            LambdaQueryWrapper<WorkComment> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(WorkComment::getWorkId, workId)
                    .eq(WorkComment::getStatus, 1);
            Long count = workCommentMapper.selectCount(wrapper);
            result.put(workId, count.intValue());
        }
        return result;
    }

    private WorkVO buildVO(Work work, String nickname) {
        WorkVO vo = new WorkVO();
        vo.setId(work.getId());
        vo.setTitle(work.getTitle());
        vo.setCoverUrl(media.imageUrl(com.zhiyu.security.UserContext.getUserId(),work.getCoverUrl()));
        vo.setMediaType(work.getMediaType());
        vo.setVideoUrl(work.getVideoUrl());
        vo.setMessage(work.getMessage());
        vo.setAnonymous(work.getAnonymous() != null && work.getAnonymous() == 1);
        vo.setAuthorName(vo.getAnonymous() ? ANONYMOUS_NAME : (nickname != null ? nickname : ANONYMOUS_NAME));
        vo.setVisible(work.getVisible());
        vo.setStatus(work.getStatus());
        vo.setShareable(Objects.equals(work.getVisible(), 1) && Objects.equals(work.getStatus(), 1));
        vo.setLikeCount(work.getLikeCount());
        vo.setCommentCount(0); // 默认 0，由调用方覆盖
        vo.setCreateTime(work.getCreateTime());
        return vo;
    }
}
