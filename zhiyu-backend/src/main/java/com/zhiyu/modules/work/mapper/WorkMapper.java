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

    /**
     * 原子更新点赞数（T1.8a）。
     *
     * @param id    作品 ID
     * @param delta 增量（+1 或 -1）
     * @return 影响行数
     */
    @Update("UPDATE work SET like_count = like_count + #{delta} WHERE id = #{id} AND deleted = 0")
    int addLikeCount(@Param("id") Long id, @Param("delta") Integer delta);
}
