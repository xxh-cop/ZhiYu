package com.zhiyu.modules.work.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 点赞/收藏请求 DTO（T1.8a）。
 */
@Data
public class LikeReq {

    /** 1点赞 0收藏（可选，默认 1） */
    @Min(value = 0, message = "type 只能是 0 或 1")
    @Max(value = 1, message = "type 只能是 0 或 1")
    private Integer type;
}
