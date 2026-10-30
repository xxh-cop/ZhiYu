package com.zhiyu.modules.heritage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.heritage.controller.HeritageController;
import com.zhiyu.modules.heritage.service.HeritageCatalogService;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link HeritageController} 单测（H1a Task 1）。
 *
 * <p>使用 standalone MockMvc，不启动 Spring 容器、不连数据库；
 * 登录态由 {@link UserContext} 直接注入，鉴权失败场景验证服务层 1002。
 */
class HeritageControllerTest {

    private MockMvc mockMvc;
    private HeritageCatalogService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new HeritageCatalogService(new ObjectMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(new HeritageController(service)).build();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("登录学生 GET /api/heritage/catalog 返回 code=0 与 4/24 目录，ID 为字符串")
    void catalogReturnsCatalogForLoggedInStudent() throws Exception {
        UserContext.set(new LoginUser(1L, 0));

        mockMvc.perform(get("/api/heritage/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.version").isString())
                .andExpect(jsonPath("$.data.sources.length()").value(4))
                .andExpect(jsonPath("$.data.traditions.length()").value(4))
                .andExpect(jsonPath("$.data.patterns.length()").value(24))
                .andExpect(jsonPath("$.data.patterns[0].id").isString())
                .andExpect(jsonPath("$.data.patterns[0].traditionId").isString());
    }

    @Test
    @DisplayName("未登录请求目录抛 BusinessException(1002)，不返回目录")
    void catalogRejectsAnonymous() {
        UserContext.clear();

        var thrown = assertThrows(Exception.class,
                () -> mockMvc.perform(get("/api/heritage/catalog")));
        var business = findCause(thrown, BusinessException.class);
        assertNotNull(business, "未登录应抛 BusinessException");
        assertEquals(1002, business.getCode());
        assertEquals("请先登录", business.getMessage());
    }

    private static <T extends Throwable> T findCause(Throwable thrown, Class<T> type) {
        Throwable current = thrown;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
