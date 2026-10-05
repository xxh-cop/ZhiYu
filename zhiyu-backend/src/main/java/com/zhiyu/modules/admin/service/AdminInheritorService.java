package com.zhiyu.modules.admin.service;

import com.zhiyu.common.dto.PageVO;
import com.zhiyu.modules.admin.dto.InheritorUpdateDTO;
import com.zhiyu.modules.admin.vo.InheritorDetailVO;
import com.zhiyu.modules.admin.vo.InheritorListItemVO;

/**
 * 管理端传承人服务（P1-B1）。
 */
public interface AdminInheritorService {

    PageVO<InheritorListItemVO> listInheritors(Integer page, Integer pageSize, Integer verified, String keyword);

    InheritorDetailVO getInheritor(Long id);

    void verifyInheritor(Long id, Integer verified);

    InheritorDetailVO updateInheritor(Long id, InheritorUpdateDTO dto);
}
