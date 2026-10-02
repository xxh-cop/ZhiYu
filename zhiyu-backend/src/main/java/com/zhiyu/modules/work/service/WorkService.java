package com.zhiyu.modules.work.service;

import com.zhiyu.modules.work.dto.WorkPublishReq;
import com.zhiyu.modules.work.vo.WorkPageVO;
import com.zhiyu.modules.work.vo.WorkVO;

/**
 * 作品服务（T1.8a）。
 */
public interface WorkService {

    /**
     * 发布作品。
     *
     * @param userId 当前用户 ID
     * @param req    发布请求
     * @return 作品 VO
     */
    WorkVO publish(Long userId, WorkPublishReq req);

    /**
     * 作品社区流（分页，只返回 status=1 AND visible=1 AND deleted=0）。
     *
     * @param page 页码（从 1 开始）
     * @param size 每页条数（上限 50）
     * @return 分页结果
     */
    WorkPageVO listWorks(Integer page, Integer size);

    /**
     * 作品详情（T1.9c-backend）。
     *
     * <p>权限规则：
     * <ul>
     *   <li>visible=1（公开）：所有人可看</li>
     *   <li>visible=2（仅小组）：M1 暂时当公开处理</li>
     *   <li>visible=0（私密）：只有作者可看，其他人 1004</li>
     * </ul>
     *
     * @param userId 当前用户 ID
     * @param workId 作品 ID
     * @return 作品 VO
     * @throws com.zhiyu.common.exception.BusinessException code=1004 如果作品不存在、未发布或无权查看
     */
    WorkVO getWork(Long userId, Long workId);
}
