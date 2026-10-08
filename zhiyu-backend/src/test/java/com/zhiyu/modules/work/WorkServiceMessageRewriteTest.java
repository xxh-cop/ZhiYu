package com.zhiyu.modules.work;

import com.zhiyu.ai.AiClient;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.work.dto.WorkPublishReq;
import com.zhiyu.modules.work.entity.Work;
import com.zhiyu.modules.work.mapper.WorkMapper;
import com.zhiyu.modules.work.service.WorkService;
import com.zhiyu.modules.work.vo.WorkVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * {@link WorkService#publish} 的 AI 生成寄语测试。
 *
 * <p>用 {@code @MockBean} 替换 {@link AiClient}，
 * 验证 AiClient 返回的文本（已含安全过滤）直接落库。
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class WorkServiceMessageRewriteTest {

    @Autowired
    private WorkService workService;

    @Autowired
    private WorkMapper workMapper;

    @Autowired
    private UserMapper userMapper;

    @MockBean
    private AiClient aiClient;

    private static final Long USER_ID = 8301L;

    @BeforeEach
    void setUp() {
        // 用户
        User u = new User();
        u.setId(USER_ID);
        u.setUsername("test_rewrite");
        u.setPassword("$2a$10$dummy");
        u.setNickname("重写测试用户");
        u.setRole(0);
        u.setStatus(1);
        u.setCanContact(0);
        u.setCreateTime(LocalDateTime.now());
        u.setUpdateTime(LocalDateTime.now());
        u.setDeleted(0);
        userMapper.insert(u);
    }

    @Test
    @DisplayName("AiClient 返回的寄语（已含安全过滤）直接落库")
    void testPublish_MessageRewritten() {
        // AiClient 内部已包含安全过滤，返回的即最终文本
        String aiMessage = "你的专注与创作本身就是一种疗愈";
        when(aiClient.workMessage(anyString(), anyString())).thenReturn(aiMessage);

        // 不传 message → 走 AI 生成
        WorkPublishReq req = new WorkPublishReq();
        req.setTitle("重写测试作品");
        req.setMediaType("text");

        WorkVO vo = workService.publish(USER_ID, req);

        // 验证落库的是 AiClient 返回的文本
        assertEquals(aiMessage, vo.getMessage());

        // 确认数据库里也是 AiClient 返回的文本
        Work work = workMapper.selectById(vo.getId());
        assertEquals(aiMessage, work.getMessage());
    }
}
