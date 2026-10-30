package com.zhiyu.ai.rag;

import com.zhiyu.ai.rag.dto.RagAskDTO;
import com.zhiyu.ai.rag.vo.RagAnswerVO;
import com.zhiyu.common.result.Result;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/ai/rag")
public class RagController {

    private final KnowledgeAskService knowledgeAskService;

    public RagController(KnowledgeAskService knowledgeAskService) {
        this.knowledgeAskService = knowledgeAskService;
        log.info("RagController 初始化: 结构化关系检索就绪");
    }

    @PostMapping("/ask")
    public Result<RagAnswerVO> ask(@Valid @RequestBody RagAskDTO dto) {
        log.info("RAG 问答请求");
        return Result.success(knowledgeAskService.ask(dto.getQuestion()));
    }
}
