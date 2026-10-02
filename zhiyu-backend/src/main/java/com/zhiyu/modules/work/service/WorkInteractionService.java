package com.zhiyu.modules.work.service;

import com.zhiyu.modules.work.vo.CommentPageVO;
import com.zhiyu.modules.work.vo.CommentVO;
import com.zhiyu.modules.work.vo.LikeVO;

/**
 * 作品互动服务（T1.8a）。
 *
 * <p>点赞/收藏 toggle + 评论 + 评论列表。
 */
public interface WorkInteractionService {

    /**
     * 点赞/收藏 toggle。
     *
     * @param userId 当前用户 ID
     * @param workId 作品 ID
     * @param type   1点赞 0收藏（null 默认 1）
     * @return toggle 后的状态
     */
    LikeVO toggleLike(Long userId, Long workId, Integer type);

    /**
     * 发表评论（经内容安全过滤）。
     *
     * @param userId  当前用户 ID
     * @param workId  作品 ID
     * @param content 评论内容
     * @return 评论 VO
     */
    CommentVO comment(Long userId, Long workId, String content);

    /**
     * 评论列表（分页，按时间正序）。
     *
     * @param workId 作品 ID
     * @param page   页码
     * @param size   每页条数
     * @return 分页结果
     */
    CommentPageVO listComments(Long workId, Integer page, Integer size);
}
