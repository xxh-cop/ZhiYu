package com.zhiyu.modules.craft.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyu.modules.craft.entity.CraftSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

/**
 * 创作会话 Mapper（T1.6 / T1.7）。
 */
@Mapper
public interface CraftSessionMapper extends BaseMapper<CraftSession> {
    @Select("SELECT id FROM craft_project WHERE id=#{projectId} AND deleted=0 FOR UPDATE")
    Long lockProject(@Param("projectId") Long projectId);

    /**
     * 原子累加心流秒数（T1.7）。
     *
     * <p>SQL 层面 {@code flow_seconds = flow_seconds + delta}，并发安全。
     *
     * @param sessionId 会话 ID
     * @param delta     增量秒数（正数）
     * @return 影响行数
     */
    @Update("UPDATE craft_session SET flow_seconds = flow_seconds + #{delta} WHERE id = #{sessionId}")
    int addFlowSeconds(@Param("sessionId") Long sessionId, @Param("delta") int delta);

    /**
     * 只更新 current_step 字段（T1.7）。
     *
     * <p>避免 updateById 覆盖 flow_seconds 等其他字段。
     *
     * @param sessionId   会话 ID
     * @param currentStep 新的步骤序号
     * @return 影响行数
     */
    @Update("UPDATE craft_session SET current_step = #{currentStep} WHERE id = #{sessionId}")
    int updateCurrentStep(@Param("sessionId") Long sessionId, @Param("currentStep") Integer currentStep);
}
