package com.zhiyu.modules.admin.service;

import com.zhiyu.common.dto.PageVO;
import com.zhiyu.modules.admin.vo.AuditLogDetailVO;
import com.zhiyu.modules.admin.vo.AuditLogItemVO;

/**
 * 管理端 AI 审计日志查询服务（P1-A1）。
 *
 * <p>提供审计日志分页查询与详情（解密）。
 * 复用 {@link com.zhiyu.modules.audit.mapper.AiAuditLogMapper}。
 */
public interface AdminAuditLogService {

    /**
     * 审计日志分页查询。
     *
     * @param page      页码（从 1 开始）
     * @param pageSize  每页大小
     * @param scene     场景筛选（可空）
     * @param action    动作筛选（可空）
     * @param startTime 起始时间（可空，格式 yyyy-MM-dd HH:mm:ss）
     * @param endTime   结束时间（可空，格式 yyyy-MM-dd HH:mm:ss）
     * @return 分页结果（列表不解密 rawInput / aiOutput）
     */
    PageVO<AuditLogItemVO> listAuditLogs(Integer page, Integer pageSize, String scene, String action,
                                          String startTime, String endTime);

    /**
     * 审计日志详情（解密 rawInput / aiOutput）。
     *
     * @param id 日志 ID
     * @return 详情 VO（含解密后的明文）
     */
    AuditLogDetailVO getAuditLog(Long id);
}
