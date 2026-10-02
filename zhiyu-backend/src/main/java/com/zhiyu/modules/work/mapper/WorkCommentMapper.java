package com.zhiyu.modules.work.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyu.modules.work.entity.WorkComment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作品评论 Mapper（T1.8a）。
 */
@Mapper
public interface WorkCommentMapper extends BaseMapper<WorkComment> {
}
