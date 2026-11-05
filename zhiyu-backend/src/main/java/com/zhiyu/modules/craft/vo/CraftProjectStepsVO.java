package com.zhiyu.modules.craft.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目步骤查询的结构化响应（2026-11-05 分层下架）。
 *
 * <p>替代旧的「非上架即 1004」硬中断：项目下架/归档后，进行中或暂停会话的用户
 * 仍能拿到步骤（回看）与当前阶段提示，而不是「资源不存在」。
 *
 * <p>仅项目不存在或待审（status=0）时仍抛 1004（不泄露存在性）。
 */
@Data
public class CraftProjectStepsVO {

    /** 步骤列表（按 stepNo 升序）；audioUrl 仅资产 ready 时下发，missing/disabled 置 null */
    private List<CraftStepVO> steps;

    /** 有效项目状态：1 上架 / 2 下架 / 3 归档（窗口或期限已过的下架按 3 下发） */
    private Integer projectStatus;

    /** 当前用户在该项目最近一个会话 ID（无则 null；雪花，JSON 字符串） */
    private Long sessionId;

    /** 当前用户在该项目最近一个会话状态：0待开始 1进行中 2已暂停 3已完成 4已放弃（无则 null） */
    private Integer sessionStatus;

    /** 会话只读：true 时不可推进步骤（心跳推进会被拒，code=3001） */
    private Boolean readOnly;

    /** 可回看历史步骤（下架/归档均为 true） */
    private Boolean canReview;

    /** 当前可导出创作数据（合规下架窗口内且 zhiyu.takedown.export-enabled=true） */
    private Boolean canExport;

    /** 私有导出/只读窗口截止（合规下架）；其他阶段为 null */
    private LocalDateTime exportUntil;

    /** 归档时间/截止（运营下架为预定截止；已归档为实际归档时间） */
    private LocalDateTime archiveAt;

    /** 提示语模板 key（notices/<key>.md），前端可做条件渲染 */
    private String noticeKey;

    /** 渲染后的提示文案（说明 + 截止时间 + 能做什么）；模板缺失时为 null，前端用 noticeKey 兜底 */
    private String notice;
}
