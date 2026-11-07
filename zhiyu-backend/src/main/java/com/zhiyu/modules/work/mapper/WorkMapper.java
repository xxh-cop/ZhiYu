package com.zhiyu.modules.work.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyu.modules.work.entity.Work;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 作品 Mapper（T1.8a）。
 */
@Mapper
public interface WorkMapper extends BaseMapper<Work> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM work WHERE id=#{id} AND deleted=0 FOR UPDATE")
    Work lockById(@Param("id") Long id);

    /**
     * 原子更新点赞数（T1.8a）。
     *
     * <p>使用 GREATEST 防止并发场景下 like_count 出现负数：
     * 即使 delta=-1 且 like_count 已为 0，结果也会被钳位到 0。
     *
     * @param id    作品 ID
     * @param delta 增量（+1 或 -1）
     * @return 影响行数
     */
    @Update("UPDATE work SET like_count = GREATEST(like_count + #{delta}, 0) WHERE id = #{id} AND deleted = 0")
    int addLikeCount(@Param("id") Long id, @Param("delta") Integer delta);

    /**
     * 社区流·位次降权版（2026-11-07 处置链，设计 §2.1）。
     *
     * <p>非软档内容按时间倒序占 1..N 位；软档内容（soft_demote_until 未过期）排序键 =
     * {@code demotePosition} + 组内位次/1000，整体落在第 N 条非软档之后、组内仍按时间倒序。
     * 仅当处置链开关开启时由 {@code WorkServiceImpl} 选用；关闭时走原 LambdaQueryWrapper
     * 纯时间序（回滚点：读侧忽略 soft_demote_until 列）。
     *
     * <p>分页说明：调用方须以 {@code Page(pageNum, size, false)}（不自动 count）调用并配合
     * {@link #selectFeedCount()} 回填 total——MyBatis-Plus 自动 count 会把 ORDER BY 包进
     * 聚合子查询，H2 2.x（测试库）拒绝该形态。
     */
    @org.apache.ibatis.annotations.Select("""
            SELECT * FROM (
              SELECT w.*,
                CASE WHEN w.soft_demote_until IS NOT NULL AND w.soft_demote_until > CURRENT_TIMESTAMP THEN 1 ELSE 0 END AS demoted,
                ROW_NUMBER() OVER (
                  PARTITION BY CASE WHEN w.soft_demote_until IS NOT NULL AND w.soft_demote_until > CURRENT_TIMESTAMP THEN 1 ELSE 0 END
                  ORDER BY w.create_time DESC, w.id DESC) AS grp_rank
              FROM work w
              WHERE w.status = 1 AND w.visible = 1 AND w.deleted = 0
            ) t
            ORDER BY CASE WHEN t.demoted = 0 THEN t.grp_rank * 1.0 ELSE #{demotePosition} + t.grp_rank / 1000.0 END
            """)
    com.baomidou.mybatisplus.core.metadata.IPage<Work> selectFeedPage(
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Work> page,
            @Param("demotePosition") int demotePosition);

    /** 社区流总数（与 {@link #selectFeedPage} 配套；筛选条件与排序无关，独立计数）。 */
    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM work WHERE status = 1 AND visible = 1 AND deleted = 0")
    long selectFeedCount();
}
