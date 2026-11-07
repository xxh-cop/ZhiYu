package com.zhiyu.modules.work.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.modules.work.entity.WorkComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 作品评论 Mapper（T1.8a）。
 */
@Mapper
public interface WorkCommentMapper extends BaseMapper<WorkComment> {

    /**
     * 作品详情评论列表·位次降权版（2026-11-07 处置链，设计 §2.1 评论同口径）。
     *
     * <p>普通评论（status=1，或本人评论）按时间正序占 1..N 位；软档评论
     * （soft_demote_until 未过期）排序键 = {@code demotePosition} + 组内位次/1000，
     * 整体落在第 N 条普通评论之后、组内仍按时间正序。仅处置链开关开启时选用。
     *
     * <p>调用方须以 {@code Page(pageNum, size, false)} 调用并配合 {@link #selectCommentCount}
     * 回填 total（MyBatis-Plus 自动 count 会把 ORDER BY 包进聚合子查询，H2 拒绝）。
     */
    @org.apache.ibatis.annotations.Select("""
            SELECT * FROM (
              SELECT c.*,
                CASE WHEN c.soft_demote_until IS NOT NULL AND c.soft_demote_until > CURRENT_TIMESTAMP THEN 1 ELSE 0 END AS demoted,
                ROW_NUMBER() OVER (
                  PARTITION BY CASE WHEN c.soft_demote_until IS NOT NULL AND c.soft_demote_until > CURRENT_TIMESTAMP THEN 1 ELSE 0 END
                  ORDER BY c.create_time ASC, c.id ASC) AS grp_rank
              FROM work_comment c
              WHERE c.work_id = #{workId} AND c.deleted = 0
                AND (c.status = 1 OR c.user_id = #{uid})
            ) t
            ORDER BY CASE WHEN t.demoted = 0 THEN t.grp_rank * 1.0 ELSE #{demotePosition} + t.grp_rank / 1000.0 END
            """)
    IPage<WorkComment> selectCommentPageDemoted(Page<WorkComment> page,
                                                @Param("workId") Long workId,
                                                @Param("uid") Long uid,
                                                @Param("demotePosition") int demotePosition);

    /** 评论列表总数（与 {@link #selectCommentPageDemoted} 配套；筛选条件与排序无关）。 */
    @org.apache.ibatis.annotations.Select("""
            SELECT COUNT(*) FROM work_comment
            WHERE work_id = #{workId} AND deleted = 0 AND (status = 1 OR user_id = #{uid})
            """)
    long selectCommentCount(@Param("workId") Long workId, @Param("uid") Long uid);
}
