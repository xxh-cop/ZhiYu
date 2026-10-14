package com.zhiyu.ai.lineart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.ai.AiClient;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.security.UserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Service
public class LineartService {
    public record Plan(String motif, int petals, int rings, boolean generated, String message) {}
    static final Plan FALLBACK = new Plan("flower", 12, 2, false, "当前使用内置花朵模板，可以继续绘色。");
    private final AiClient ai;
    private final ObjectMapper json;
    private final UserMapper users;
    private final String prompt;

    public LineartService(AiClient ai, ObjectMapper json, UserMapper users,
                          @Value("classpath:prompts/lineart.st") Resource prompt) throws IOException {
        this.ai=ai;this.json=json;this.users=users;
        try(var in=prompt.getInputStream()){this.prompt=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
    }

    public Plan generate(String theme, boolean consent) {
        Long id=UserContext.getUserId();
        if(id==null)throw new BusinessException(1002,"请先登录");
        var user=users.selectById(id);
        if(user==null||!Integer.valueOf(1).equals(user.getStatus()))throw new BusinessException(1003,"账号不可用");
        if(!consent||!Integer.valueOf(1).equals(user.getPrivacyAgreed()))throw new BusinessException(1001,"请先同意隐私政策和本次 AI 生成授权");
        var response=ai.lineartPlan(theme,prompt.replace("{theme}",theme));
        return response.generated()?parse(response.text()):FALLBACK;
    }

    /** Treat model output as untrusted data: accept only a small, bounded numeric recipe. */
    Plan parse(String response) {
        try {
            if(response==null||response.length()>1000)return FALLBACK;
            JsonNode node=json.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(response);
            if(!node.isObject()||node.size()!=3||!node.path("motif").isTextual()
                ||!node.path("petals").isIntegralNumber()||!node.path("rings").isIntegralNumber()
                ||!node.path("petals").canConvertToInt()||!node.path("rings").canConvertToInt())return FALLBACK;
            String motif=node.path("motif").asText();int petals=node.path("petals").intValue(),rings=node.path("rings").intValue();
            if(!Set.of("flower","leaf","ripple").contains(motif)||petals<6||petals>16||rings<1||rings>3)return FALLBACK;
            return new Plan(motif,petals,rings,true,"AI 已为你搭配线稿结构，可继续换色或调整。不是心理评估。");
        }catch(Exception ignored){return FALLBACK;}
    }
}
