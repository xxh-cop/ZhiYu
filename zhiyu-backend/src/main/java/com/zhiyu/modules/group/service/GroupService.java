package com.zhiyu.modules.group.service;

import com.zhiyu.modules.group.dto.GroupCreateReq;
import com.zhiyu.modules.group.vo.GroupVO;

import java.util.List;

/**
 * 互助小组服务（T1.8b）。
 */
public interface GroupService {

    /**
     * 小组列表（只返回 status=1 AND deleted=0）。
     *
     * @param userId 当前用户 ID（用于判断 joined / myRole）
     * @return 小组 VO 列表
     */
    List<GroupVO> listGroups(Long userId);

    /**
     * 创建小组（创建者自动成为组长）。
     *
     * @param userId 当前用户 ID
     * @param req    创建请求
     * @return 小组 VO
     */
    GroupVO createGroup(Long userId, GroupCreateReq req);

    /**
     * 加入小组。
     *
     * @param userId  当前用户 ID
     * @param groupId 小组 ID
     * @return 小组 VO
     */
    GroupVO joinGroup(Long userId, Long groupId);
}
