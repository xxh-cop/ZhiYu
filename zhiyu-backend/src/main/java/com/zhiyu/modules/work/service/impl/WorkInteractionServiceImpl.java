package com.zhiyu.modules.work.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.ai.safety.SafetyAction;
import com.zhiyu.ai.safety.SafetyResult;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.work.entity.Work;
import com.zhiyu.modules.work.entity.WorkComment;
import com.zhiyu.modules.work.entity.WorkLike;
import com.zhiyu.modules.work.mapper.WorkCommentMapper;
import com.zhiyu.modules.work.mapper.WorkLikeMapper;
import com.zhiyu.modules.work.mapper.WorkMapper;
import com.zhiyu.modules.work.service.WorkInteractionService;
import com.zhiyu.modules.work.vo.CommentPageVO;
import com.zhiyu.modules.work.vo.CommentVO;
import com.zhiyu.modules.work.vo.LikeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 作品互动服务实现（T1.8a）。
 *
 * <p>点赞/收藏 toggle + 评论（经内容安全过滤）+ 评论列表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkInteractionServiceImpl implements WorkInteractionService {

    private final com.zhiyu.modules.work.service.WorkAudience audience;
    private final WorkMapper workMapper;
    private final WorkCommentMapper workCommentMapper;
    private final WorkLikeMapper workLikeMapper;
    private final UserMapper userMapper;
    private final ContentSafetyFilter contentSafetyFilter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LikeVO toggleLike(Long userId, Long workId, Integer type) {
        // 1. 校验作品存在且已发布
        Work work = workMapper.lockById(workId);
        if (!audience.canRead(work,userId)) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        int likeType = (type != null) ? type : 1;

        // 2. 查 work_like（含已删除记录，用原生 SQL 绕过 @TableLogic）
        WorkLike existing = workLikeMapper.findOneIncludeDeleted(workId, userId, likeType);

        if (existing != null && existing.getDeleted() != null && existing.getDeleted() == 0) {
            // 情况 A：存在且未删除 → 取消
            workLikeMapper.softDelete(existing.getId(), LocalDateTime.now());
            if (likeType == 1) {
                workMapper.addLikeCount(workId, -1);
            }
            log.info("取消点赞: workId={}, userId={}, type={}", workId, userId, likeType);
            return new LikeVO(false);
        }

        if (existing == null) {
            // 情况 B：不存在 → 新增
            WorkLike like = new WorkLike();
            like.setWorkId(workId);
            like.setUserId(userId);
            like.setType(likeType);
            workLikeMapper.insert(like);
        } else {
            // 情况 C：存在但 deleted=1 → 恢复
            workLikeMapper.restore(existing.getId(), LocalDateTime.now());
        }

        if (likeType == 1) {
            workMapper.addLikeCount(workId, 1);
        }
        log.info("点赞/收藏: workId={}, userId={}, type={}", workId, userId, likeType);
        return new LikeVO(true);
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public CommentVO comment(Long userId, Long workId, String content) {
        // 1. 校验作品存在且已发布
        Work work = workMapper.lockById(workId);
        if (!audience.canRead(work,userId)) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        // 2. 内容安全过滤
        SafetyResult sr = contentSafetyFilter.checkInput(userId, null, "comment", content);
        if (sr.getAction() == SafetyAction.BLOCK) {
            log.info("评论被安全过滤拦截: workId={}, userId={}, reason={}", workId, userId, sr.getReason());
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), sr.getCareMessage());
        }
        // 防御：输入侧理论上不会 REWRITE，但防御性处理
        if (sr.getAction() == SafetyAction.REWRITE && sr.getRewrittenText() != null) {
            content = sr.getRewrittenText();
        }

        // 3. 插入评论
        WorkComment comment = new WorkComment();
        comment.setWorkId(workId);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setStatus(0);
        workCommentMapper.insert(comment);
        log.info("发表评论: commentId={}, workId={}, userId={}", comment.getId(), workId, userId);

        // 4. 返回 VO
        String nickname = getNickname(userId);
        return buildCommentVO(comment, nickname);
    }

    @Override
    public CommentPageVO listComments(Long workId, Integer page, Integer size) {
        // 1. 校验作品存在
        Work work = workMapper.selectById(workId);
        if (!audience.canRead(work,com.zhiyu.security.UserContext.getUserId())) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        int pageNum = (page == null || page < 1) ? 1 : page;
        int pageSize = (size == null || size < 1) ? 10 : Math.min(size, 50);

        // 2. 分页查评论 where work_id=? AND status=1 AND deleted=0，按 create_time asc
        LambdaQueryWrapper<WorkComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkComment::getWorkId, workId)
                .and(q -> q.eq(WorkComment::getStatus,1).or(com.zhiyu.security.UserContext.getUserId()!=null).eq(com.zhiyu.security.UserContext.getUserId()!=null,WorkComment::getUserId,com.zhiyu.security.UserContext.getUserId()))
                .orderByAsc(WorkComment::getCreateTime);

        IPage<WorkComment> pageResult = workCommentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // 3. 批量查昵称
        Set<Long> userIds = pageResult.getRecords().stream()
                .map(WorkComment::getUserId)
                .collect(Collectors.toSet());
        Map<Long, String> nicknameMap = batchGetNicknames(userIds);

        // 4. 组装 VO
        List<CommentVO> voList = pageResult.getRecords().stream()
                .map(c -> buildCommentVO(c, nicknameMap.get(c.getUserId())))
                .collect(Collectors.toList());

        CommentPageVO result = new CommentPageVO();
        result.setTotal(pageResult.getTotal());
        result.setPage(pageNum);
        result.setSize(pageSize);
        result.setRecords(voList);
        return result;
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

    private CommentVO buildCommentVO(WorkComment comment, String nickname) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setStatus(comment.getStatus());
        vo.setMine(Objects.equals(comment.getUserId(),com.zhiyu.security.UserContext.getUserId()));
        vo.setContent(comment.getContent());
        vo.setAuthorName(nickname != null ? nickname : "未知用户");
        vo.setCreateTime(comment.getCreateTime());
        return vo;
    }
}
