package com.zhiyu.modules.emotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyu.modules.emotion.entity.EmotionNote;
import org.apache.ibatis.annotations.Mapper;

/**
 * 心情便签 Mapper（M3-A1）。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，CRUD 由框架提供。
 * 查询时 Service 层强制 {@code WHERE user_id = current}（仅本人可见）。
 */
@Mapper
public interface EmotionNoteMapper extends BaseMapper<EmotionNote> {
}
