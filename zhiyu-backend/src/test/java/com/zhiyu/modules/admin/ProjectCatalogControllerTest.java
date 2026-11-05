package com.zhiyu.modules.admin;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.admin.controller.ProjectCatalogController;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BT（2026-11-05）：管理端项目目录放开 status=3（归档）可见性。
 * 白名单区间 0..3；4+ 与负数仍 1001。仅 role=3 路径（access.role(3) 闸门）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectCatalogControllerTest {

    @Autowired
    private ProjectCatalogController controller;
    @Autowired
    private JdbcTemplate jdbc;

    private static final long ADMIN = 960001L;
    private static final long INHERITOR = 960002L;

    @BeforeEach
    void setup() {
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?,?, 'unused',?,?,1,1,0,0)", ADMIN, "pc_admin", "管理员", 3);
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?,?, 'unused',?,?,1,1,0,0)", INHERITOR, "pc_inh", "传承人", 1);
        // 三个状态各一项目（同类目，排除默认数据干扰用 keyword 精确捞）
        jdbc.update("INSERT INTO craft_project(id,inheritor_id,title,level,category,status,create_time,update_time,deleted)"
                + " VALUES(960100,?, 'BT目录上架',1,'纸艺',1,NOW(),NOW(),0)", INHERITOR);
        jdbc.update("INSERT INTO craft_project(id,inheritor_id,title,level,category,status,create_time,update_time,deleted)"
                + " VALUES(960101,?, 'BT目录下架',1,'纸艺',2,NOW(),NOW(),0)", INHERITOR);
        jdbc.update("INSERT INTO craft_project(id,inheritor_id,title,level,category,status,create_time,update_time,deleted)"
                + " VALUES(960102,?, 'BT目录归档',1,'纸艺',3,NOW(),NOW(),0)", INHERITOR);
        UserContext.set(new LoginUser(ADMIN, 3));
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    @DisplayName("BT：status=3 可查询，归档项目出现在结果中；status=1/2 口径不变")
    void archivedStatusQueryable() {
        var data = (Map<String, Object>) controller.list(1, 3, "", "BT目录").getData();
        var records = (List<Map<String, Object>>) data.get("records");
        assertEquals(1, records.size(), "status=3 应捞到归档项目");
        assertEquals("960102", records.get(0).get("id"));
        assertEquals(3, ((Number) records.get(0).get("status")).intValue());

        var live = (Map<String, Object>) controller.list(1, 1, "", "BT目录").getData();
        assertEquals(1, ((List<?>) live.get("records")).size(), "status=1 口径不变");
    }

    @Test
    @DisplayName("BT：非法 status（4 / 负数）仍 1001")
    void illegalStatusRejected() {
        assertEquals(1001, assertThrows(BusinessException.class,
                () -> controller.list(1, 4, "", "")).getCode(), "status=4 应 1001");
        assertEquals(1001, assertThrows(BusinessException.class,
                () -> controller.list(1, -1, "", "")).getCode(), "负数 status 应 1001");
    }
}
