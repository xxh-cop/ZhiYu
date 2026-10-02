package com.zhiyu.modules.work.vo;

import lombok.Data;

/**
 * 点赞/收藏 toggle 结果 VO（T1.8a）。
 */
@Data
public class LikeVO {

    /** true=已点赞/已收藏；false=本次请求执行了取消 */
    private Boolean active;

    public LikeVO(Boolean active) {
        this.active = active;
    }
}
