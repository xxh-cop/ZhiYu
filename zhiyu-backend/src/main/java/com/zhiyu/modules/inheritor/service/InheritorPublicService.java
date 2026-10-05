package com.zhiyu.modules.inheritor.service;

import com.zhiyu.common.dto.PageVO;
import com.zhiyu.modules.inheritor.vo.InheritorPublicDetailVO;
import com.zhiyu.modules.inheritor.vo.InheritorPublicItemVO;

/**
 * 传承人公开查询 Service（M2 批 6A）。
 *
 * <p>学生端可见的传承人信息查询，只返回 {@code verified=1} 的传承人。
 */
public interface InheritorPublicService {

    /**
     * 分页查询已认证传承人列表。
     *
     * @param page     页码（从 1 开始）
     * @param pageSize 每页大小
     * @param keyword  关键词（可选，匹配 skill 或 user.nickname）
     * @return 分页结果
     */
    PageVO<InheritorPublicItemVO> listInheritors(int page, int pageSize, String keyword);

    /**
     * 查询传承人详情。
     *
     * @param id inheritor_profile.id
     * @return 传承人详情
     */
    InheritorPublicDetailVO getInheritor(Long id);
}
