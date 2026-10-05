package com.zhiyu.modules.admin.service;

import com.zhiyu.common.dto.PageVO;
import com.zhiyu.modules.admin.vo.GroupDetailVO;
import com.zhiyu.modules.admin.vo.GroupListItemVO;

/**
 * 管理端小组服务（P1-B1）。
 */
public interface AdminGroupService {

    PageVO<GroupListItemVO> listGroups(Integer page, Integer pageSize, Integer status, String keyword);

    GroupDetailVO getGroup(Long id);

    void updateGroupStatus(Long id, Integer status);

    void deleteGroup(Long id);
}
