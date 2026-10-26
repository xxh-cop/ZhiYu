package com.zhiyu.modules.group.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyu.modules.group.entity.GroupMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 小组成员 Mapper（T1.8b）。
 */
@Mapper
public interface GroupMemberMapper extends BaseMapper<GroupMember> {
    @org.apache.ibatis.annotations.Update("UPDATE group_member SET deleted=0,role='成员',update_time=CURRENT_TIMESTAMP WHERE group_id=#{groupId} AND user_id=#{userId} AND deleted=1")
    int restore(@org.apache.ibatis.annotations.Param("groupId") Long groupId,@org.apache.ibatis.annotations.Param("userId") Long userId);
}
