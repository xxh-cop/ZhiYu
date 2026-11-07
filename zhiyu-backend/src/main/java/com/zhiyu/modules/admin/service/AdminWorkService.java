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

    /**
     * 带软档标记的状态变更（2026-11-07 处置链）：softTier=true 仅允许随 status=1（通过）使用，
     * 通过后由 {@code SoftTierRecordService} 落台账+抽样+降权+通知（同一事务）。
     * narrative / riskPointsNonEmpty 为管理端回显的 AI 建议信息（抽样信号与留证，可空）。
     */
    void updateWorkStatus(Long id, Integer status, String reason, Boolean softTier,
                          String narrative, Boolean riskPointsNonEmpty);

    void deleteWork(Long id);
}
