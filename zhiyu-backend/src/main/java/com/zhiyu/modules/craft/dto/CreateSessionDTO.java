package com.zhiyu.modules.craft.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建会话请求 DTO（T1.6）。
 */
@Data
public class CreateSessionDTO {

    @NotNull(message = "项目 ID 不能为空")
    private Long projectId;

    /** 关联的情绪会话 ID（可空，从情绪对话→推荐→创作流程中传入） */
    private Long emotionSessionId;
}
