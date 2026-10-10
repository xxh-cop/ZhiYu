package com.zhiyu.modules.user.vo;

import lombok.Data;

/**
 * 学生端「我的」统计数据 VO（批次 6-E）。
 *
 * <p>对应 GET /api/user/stats 接口返回体。
 */
@Data
public class UserStatsVO {

    /** 注册天数（今天注册返回 1） */
    private Long daysSinceRegister;

    /** 作品数（work WHERE user_id AND deleted=0） */
    private Long workCount;

    /** 便签数（emotion_note WHERE user_id AND deleted=0） */
    private Long noteCount;
}
