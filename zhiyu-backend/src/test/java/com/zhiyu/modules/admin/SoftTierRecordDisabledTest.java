package com.zhiyu.modules.admin;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.work.service.SoftTierRecordService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 处置链开关关闭（默认）语义（纯单测，不起 Spring，不依赖数据库）：
 * 关闭时写池与申诉一律 1001——这是 G0/G2 回滚点的兜底保证。
 */
class SoftTierRecordDisabledTest {

    @Test
    @DisplayName("开关关闭：record 与 appeal 均拒绝（1001）")
    void disabledRejects() {
        SoftTierRecordService svc = new SoftTierRecordService(null, null, null, null);
        ReflectionTestUtils.setField(svc, "enabled", false);
        BusinessException r = assertThrows(BusinessException.class,
                () -> svc.record("work", 1L, 2L, "内容", null, false, "标题"));
        assertEquals(1001, r.getCode());
        BusinessException a = assertThrows(BusinessException.class,
                () -> svc.appeal(2L, "work", 1L, "申诉理由"));
        assertEquals(1001, a.getCode());
    }
}
