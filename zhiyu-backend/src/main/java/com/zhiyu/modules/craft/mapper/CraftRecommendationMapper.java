package com.zhiyu.modules.craft.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyu.modules.craft.entity.CraftRecommendation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 推荐主表 Mapper（T1.5）。
 */
@Mapper
public interface CraftRecommendationMapper extends BaseMapper<CraftRecommendation> {
}
