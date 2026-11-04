package com.zhiyu.modules.work.vo;

import lombok.Data;

import java.util.List;

/**
 * 作品分页 VO（T1.8a）。
 *
 * <p>AE3（2026-11-04）：total 为 Integer —— 序列化为 JSON number，与 PageVO 口径一致
 * （避免 JacksonConfig 全局 Long→String 把计数误写成字符串；雪花 id 不受影响，仍为字符串）。
 */
@Data
public class WorkPageVO {

    private Integer total;
    private Integer page;
    private Integer size;
    private List<WorkVO> records;
}
