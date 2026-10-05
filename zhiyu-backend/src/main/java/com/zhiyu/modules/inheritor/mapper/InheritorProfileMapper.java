package com.zhiyu.modules.inheritor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyu.modules.inheritor.entity.InheritorProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 传承人资料 Mapper。
 *
 * <p>全部走 MyBatis-Plus {@link BaseMapper}，无自定义 SQL。
 */
@Mapper
public interface InheritorProfileMapper extends BaseMapper<InheritorProfile> {
}
