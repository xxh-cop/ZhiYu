package com.zhiyu.modules.dialogue.capability;

import com.zhiyu.ai.rag.KnowledgeAskService;
import com.zhiyu.ai.rag.vo.RagAnswerVO;
import com.zhiyu.modules.dialogue.DialogueCapability;
import com.zhiyu.modules.dialogue.DialogueTurnRequest;
import com.zhiyu.modules.dialogue.DialogueTurnResult;
import com.zhiyu.security.BusinessAccess;
import org.springframework.stereotype.Component;

/**
 * 非遗知识子能力（P2）：薄委托现有 RAG 问答链（KnowledgeAskService）。
 * 来源引用信息在 P2 协议（chunk/revoke/done）中不外发，仅作答正文按单 chunk 下发；
 * 完整引用仍由 /api/heritage/** 原端点承载。
 *
 * <p>隔离约束：不得 import 其他 capability 实现类（见 {@link DialogueCapability}）。
 */
@Component
public class KnowledgeCapability implements DialogueCapability {

    private final KnowledgeAskService knowledgeAskService;
    private final BusinessAccess access;

    public KnowledgeCapability(KnowledgeAskService knowledgeAskService, BusinessAccess access) {
        this.knowledgeAskService = knowledgeAskService;
        this.access = access;
    }

    @Override
    public String id() {
        return "KNOWLEDGE";
    }

    @Override
    public Long ensureSession(DialogueTurnRequest req) {
        return req.sessionId(); // 知识问答无会话概念
    }

    @Override
    public DialogueTurnResult execute(DialogueTurnRequest req) {
        access.consentedUser();
        RagAnswerVO answer = knowledgeAskService.ask(req.text());
        String reply = answer.getAnswer() == null ? "" : answer.getAnswer();
        if (!reply.isBlank()) {
            req.chunkSink().accept(reply);
        }
        return new DialogueTurnResult(req.sessionId(), id(), reply, false, false, false);
    }
}
