package com.zhiyu.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyu.modules.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code user} 表的 Mapper。
 *
 * <p>不加自定义方法，全部走 MyBatis-Plus 的 {@link BaseMapper}；
 * 逻辑删除与 create_time/update_time 自动填充由全局配置和
 * {@code MetaObjectFillHandler} 负责。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
