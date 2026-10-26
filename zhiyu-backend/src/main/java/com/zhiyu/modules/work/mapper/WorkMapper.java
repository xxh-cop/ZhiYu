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
}
