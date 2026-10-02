package com.zhiyu.modules.craft.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyu.modules.craft.entity.CraftStep;
import org.apache.ibatis.annotations.Mapper;

/**
 * 手作步骤 Mapper（T1.6）。
 *
 * <p>本批次仅用于 count totalSteps，不涉及步骤推进逻辑（T1.7）。
 */
@Mapper
public interface CraftStepMapper extends BaseMapper<CraftStep> {
}
