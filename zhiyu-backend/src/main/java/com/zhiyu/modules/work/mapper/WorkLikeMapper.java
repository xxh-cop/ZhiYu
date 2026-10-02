package com.zhiyu.modules.work.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyu.modules.work.entity.WorkLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 作品点赞/收藏 Mapper（T1.8a）。
 *
 * <p>toggle 逻辑需要查到已逻辑删除的记录（恢复场景），
 * 故提供绕过 {@code @TableLogic} 的原生 SQL 查询。
 */
@Mapper
public interface WorkLikeMapper extends BaseMapper<WorkLike> {

    /**
     * 查找（含已删除），用于 toggle 判断。
     */
    @Select("SELECT * FROM work_like WHERE work_id = #{workId} AND user_id = #{userId} AND type = #{type} LIMIT 1")
    WorkLike findOneIncludeDeleted(@Param("workId") Long workId,
                                   @Param("userId") Long userId,
                                   @Param("type") Integer type);

    /**
     * 软删除（设置 deleted=1），绕过逻辑删除拦截器。
     */
    @Update("UPDATE work_like SET deleted = 1, update_time = #{now} WHERE id = #{id}")
    int softDelete(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * 恢复（设置 deleted=0），绕过逻辑删除拦截器。
     */
    @Update("UPDATE work_like SET deleted = 0, update_time = #{now} WHERE id = #{id}")
    int restore(@Param("id") Long id, @Param("now") LocalDateTime now);
}
