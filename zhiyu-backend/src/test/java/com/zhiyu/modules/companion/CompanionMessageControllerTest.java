package com.zhiyu.modules.companion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.companion.controller.CompanionMessageController;
import com.zhiyu.modules.companion.tool.ToolContext;
import com.zhiyu.modules.practice.enums.PracticeSessionStatus;
import com.zhiyu.modules.practice.service.PracticeSessionService;
import com.zhiyu.modules.practice.vo.PracticeSessionVO;
import com.zhiyu.security.BusinessAccess;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompanionMessageControllerTest {

    private BusinessAccess access;
    private PracticeSessionService practiceService;
    private CompanionOrchestrator orchestrator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        access = mock(BusinessAccess.class);
        practiceService = mock(PracticeSessionService.class);
        orchestrator = mock(CompanionOrchestrator.class);
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(executor).execute(any(Runnable.class));

        mockMvc = MockMvcBuilders.standaloneSetup(new CompanionMessageController(
                access, practiceService, orchestrator, new ObjectMapper(), executor)).build();
        UserContext.set(new LoginUser(7L, 0));
        when(access.consentedUser()).thenReturn(7L);
        when(practiceService.get(7L, 99L)).thenReturn(session());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("SSE 按 stage/message/tool/citation/done 输出")
    void streamsTypedEvents() throws Exception {
        when(orchestrator.respond(any(ToolContext.class), any(), anyString())).thenReturn(turn());

        MvcResult result = mockMvc.perform(post("/api/companion/sessions/99/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"content\":\"想找点事做\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:stage")))
                .andExpect(content().string(containsString("\"stage\":\"RECOMMEND\"")))
                .andExpect(content().string(containsString("event:message")))
                .andExpect(content().string(containsString("\"generated\":true")))
                .andExpect(content().string(containsString("event:tool")))
                .andExpect(content().string(containsString("\"name\":\"recommendPractice\"")))
                .andExpect(content().string(containsString("event:citation")))
                .andExpect(content().string(containsString("event:done")));
    }

    @Test
    @DisplayName("运行期异常以 event:error 返回且 HTTP 保持 200")
    void sendsErrorEvent() throws Exception {
        when(orchestrator.respond(any(ToolContext.class), any(), anyString()))
                .thenThrow(new BusinessException(4001, "小织暂时不可用"));

        MvcResult result = mockMvc.perform(post("/api/companion/sessions/99/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"content\":\"在吗\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:error")))
                .andExpect(content().string(containsString("\"code\":4001")));
    }

    @Test
    @DisplayName("空白消息在进入 SSE 前被参数校验拒绝")
    void blankMessageRejected() throws Exception {
        mockMvc.perform(post("/api/companion/sessions/99/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    private PracticeSessionVO session() {
        PracticeSessionVO session = new PracticeSessionVO();
        session.setPracticeSessionId(99L);
        session.setKind("HERITAGE");
        session.setStatus(PracticeSessionStatus.RECOMMENDED.getCode());
        session.setStatusLabel(PracticeSessionStatus.RECOMMENDED.getLabel());
        session.setMinutesBudget(15);
        session.setCurrentStep(1);
        return session;
    }

    private CompanionTurn turn() {
        PracticeSessionVO session = session();
        List<CompanionEvent> events = List.of(
                CompanionEvent.stage(CompanionStage.RECOMMEND, session),
                CompanionEvent.message("我先按你的时间预算找两个练习。", true),
                CompanionEvent.tool("recommendPractice", Map.of("items", List.of())),
                CompanionEvent.citation(Map.of("id", "src-1", "citationType", "VERIFIED_SOURCE")),
                CompanionEvent.done(CompanionStage.RECOMMEND, session, true, false, true));
        return new CompanionTurn(events, "我先按你的时间预算找两个练习。",
                CompanionStage.RECOMMEND, true, false, true, "recommendPractice");
    }
}
