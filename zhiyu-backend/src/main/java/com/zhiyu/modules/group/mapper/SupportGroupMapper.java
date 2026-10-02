package com.zhiyu.modules.group.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyu.modules.group.entity.SupportGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 互助小组 Mapper（T1.8b）。
 */
@Mapper
public interface SupportGroupMapper extends BaseMapper<SupportGroup> {

    /**
     * 原子递增成员数（带并发保护）。
     *
     * <p>仅当 {@code member_count < max_count} 时才 +1，返回影响行数。
     * 返回 0 表示小组已满或不存在。
     *
     * @param id 小组 ID
     * @return 影响行数（1=成功，0=已满/不存在）
     */
    @Update("UPDATE support_group SET member_count = member_count + 1 " +
            "WHERE id = #{id} AND member_count < max_count AND deleted = 0")
    int incrementMemberCount(@Param("id") Long id);
}
