package com.zhiyu.modules.admin.service;

import com.zhiyu.common.dto.PageVO;
import com.zhiyu.modules.admin.vo.WorkDetailVO;
import com.zhiyu.modules.admin.vo.WorkListItemVO;

/**
 * 管理端作品服务（P1-B1）。
 */
public interface AdminWorkService {

    PageVO<WorkListItemVO> listWorks(Integer page, Integer pageSize, Integer status, Integer visible, String keyword);

    WorkDetailVO getWork(Long id);

    void updateWorkStatus(Long id, Integer status);

    /** 带审核理由的状态变更（I1，2026-11-04）：下架/驳回必须携带 reason。 */
    void updateWorkStatus(Long id, Integer status, String reason);

    void deleteWork(Long id);
}
