package com.zhiyu.ai.rag;

import com.zhiyu.ai.rag.dto.RagAskDTO;
import com.zhiyu.ai.rag.vo.RagAnswerVO;
import com.zhiyu.common.result.Result;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 问答接口（M3-3）。
 *
 * <p>鉴权：authenticated（SecurityConfig 默认规则，无需改 SecurityConfig）。
 * 路径 {@code /api/ai/rag/ask} 不在 {@code /api/admin/**} 下，
 * 所有已登录用户（含学生）均可访问。
 *
 * <p>当 RAG 服务未就绪（未配置豆包 API Key）时，
 * {@link RagService} 不存在（{@code @ConditionalOnBean} 守卫），
 * 本 Controller 通过 {@code required=false} 注入拿到 {@code null}，
 * 调用时返回 {@code Result(4001)} 降级提示。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/rag")
public class RagController {

    private final RagService ragService;

    @Autowired
    public RagController(@org.springframework.lang.Nullable RagService ragService) {
        this.ragService = ragService;
        if (ragService == null) {
            log.info("RagController 初始化: RAG 服务未就绪（未配置 DOUBAO_API_KEY），问答接口将返回降级提示");
        } else {
            log.info("RagController 初始化: RAG 服务就绪");
        }
    }

    @PostMapping("/ask")
    public Result<RagAnswerVO> ask(@Valid @RequestBody RagAskDTO dto) {
        if (ragService == null) {
            return Result.error(4001, "非遗问答服务暂未开启，请稍后再试");
        }
        log.info("RAG 问答请求");
        RagAnswerVO result = ragService.ask(dto.getQuestion());
        return Result.success(result);
    }
}
