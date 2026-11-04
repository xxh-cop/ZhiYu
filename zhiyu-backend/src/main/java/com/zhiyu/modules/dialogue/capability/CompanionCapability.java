package com.zhiyu.modules.dialogue.capability;

import com.zhiyu.modules.companion.CompanionOrchestrator;
import com.zhiyu.modules.companion.CompanionTurn;
import com.zhiyu.modules.companion.tool.ToolContext;
import com.zhiyu.modules.dialogue.DialogueCapability;
import com.zhiyu.modules.dialogue.DialogueTurnRequest;
import com.zhiyu.modules.dialogue.DialogueTurnResult;
import com.zhiyu.modules.practice.service.PracticeSessionService;
import com.zhiyu.modules.practice.vo.PracticeSessionVO;
import com.zhiyu.security.BusinessAccess;
import org.springframework.stereotype.Component;

/**
 * 练习陪伴子能力（P2）：薄委托 {@link CompanionOrchestrator}（编排逻辑原样复用，不迁入）。
 *
 * <p>P2 协议收敛为 chunk/revoke/done：编排器产出的 stage/tool/citation/artifact 富事件
 * 在调度层不外发（本批协议只保留三事件，见任务书 AA5.1）；reply 作为单 chunk 下发。
 * typed 事件全量保留在 /api/companion/sessions/{id}/message 原端点（P3 适配器另行处理）。
 *
 * <p>隔离约束：不得 import 其他 capability 实现类（见 {@link DialogueCapability}）。
 */
@Component
public class CompanionCapability implements DialogueCapability {

    private final CompanionOrchestrator orchestrator;
    private final PracticeSessionService practiceService;
    private final BusinessAccess access;

    public CompanionCapability(CompanionOrchestrator orchestrator,
                               PracticeSessionService practiceService,
                               BusinessAccess access) {
        this.orchestrator = orchestrator;
        this.practiceService = practiceService;
        this.access = access;
    }

    @Override
    public String id() {
        return "PRACTICE_COMPANION";
    }

    @Override
    public Long ensureSession(DialogueTurnRequest req) {
        return req.practiceSessionId();
    }

    /** 练习会话是否处于可对话的活跃状态（路由 R1 的判定输入）。 */
    public boolean sessionActive(Long userId, Long practiceSessionId) {
        if (practiceSessionId == null) {
            return false;
        }
        try {
            PracticeSessionVO vo = practiceService.get(userId, practiceSessionId);
            Integer status = vo.getStatus();
            // 终态（已完成/已保存/已放弃）视为不活跃，其余可对话
            return status != null && status != 5 && status != 6 && status != 7;
        } catch (RuntimeException e) {
            return false; // 不存在/越权 → 不活跃，路由回落倾听
        }
    }

    @Override
    public DialogueTurnResult execute(DialogueTurnRequest req) {
        long userId = req.userId();
        access.consentedUser();
        PracticeSessionVO session = practiceService.get(userId, req.practiceSessionId());
        CompanionTurn turn = orchestrator.respond(
                new ToolContext(userId, req.practiceSessionId()), session, req.text());
        if (turn.reply() != null && !turn.reply().isBlank()) {
            req.chunkSink().accept(turn.reply());
        }
        return new DialogueTurnResult(req.practiceSessionId(), id(), turn.reply(),
                turn.blocked(), false, turn.toolUsed());
    }
}
