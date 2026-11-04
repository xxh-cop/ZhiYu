package com.zhiyu.modules.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyu.modules.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@link Notification} 的 MyBatis-Plus Mapper。
 * 逻辑删除由 {@code @TableLogic} 自动附加 {@code deleted=0}。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
