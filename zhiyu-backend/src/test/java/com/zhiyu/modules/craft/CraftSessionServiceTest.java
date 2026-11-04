package com.zhiyu.modules.craft;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.entity.CraftSession;
import com.zhiyu.modules.craft.entity.CraftStep;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.craft.mapper.CraftSessionMapper;
import com.zhiyu.modules.craft.mapper.CraftStepMapper;
import com.zhiyu.modules.craft.service.CraftSessionService;
import com.zhiyu.modules.craft.vo.CraftSessionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CraftSessionService} 集成测试（T1.6）。
 *
 * <p>@SpringBootTest + H2，不依赖 init-data.sql。
 * 用 @DirtiesContext(AFTER_CLASS) 避免数据污染（参考 EmotionControllerTest）。
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class CraftSessionServiceTest {

    @Autowired
    private CraftSessionService craftSessionService;

    @Autowired
    private CraftProjectMapper craftProjectMapper;

    @Autowired
    private CraftSessionMapper craftSessionMapper;

    @Autowired
    private CraftStepMapper craftStepMapper;

    private static final Long USER_ID = 7001L;
    private static final Long OTHER_USER_ID = 7002L;
    private static final Long PROJECT_ID = 9001L;
    private static final Long UNPUBLISHED_PROJECT_ID = 9002L;

    @BeforeEach
    void setUp() {
        // 上架项目
        insertProject(PROJECT_ID, "编织入门", 1);
        // 未上架项目
        insertProject(UNPUBLISHED_PROJECT_ID, "未上线项目", 0);
        // 步骤
        insertStep(PROJECT_ID, 1, "准备材料");
        insertStep(PROJECT_ID, 2, "开始编织");
        insertStep(PROJECT_ID, 3, "收尾整理");
    }

    @Test
    @DisplayName("创建成功：status=1，currentStep=1，startTime 非空")
    void testCreate_Success() {
        CraftSessionVO vo = craftSessionService.create(USER_ID, PROJECT_ID, null);

        assertNotNull(vo.getSessionId());
        assertEquals(PROJECT_ID, vo.getProjectId());
        assertEquals("编织入门", vo.getProjectTitle());
        assertEquals(1, vo.getStatus());
        assertEquals("进行中", vo.getStatusLabel());
        assertEquals(1, vo.getCurrentStep());
        assertEquals(3, vo.getTotalSteps());
        assertNotNull(vo.getStartTime());
        assertNull(vo.getEndTime());
    }

    @Test
    @DisplayName("创建失败：项目不存在 → 1004")
    void testCreate_ProjectNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> craftSessionService.create(USER_ID, 9999L, null));
        assertEquals(1004, ex.getCode());
    }

    @Test
    @DisplayName("创建失败：项目未上架 → 1004（AY1：不泄露项目状态）")
    void testCreate_ProjectNotPublished() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> craftSessionService.create(USER_ID, UNPUBLISHED_PROJECT_ID, null));
        assertEquals(1004, ex.getCode());
    }

    @Test
    @DisplayName("创建失败：已有进行中会话 → 3001")
    void testCreate_DuplicateActiveSession() {
        craftSessionService.create(USER_ID, PROJECT_ID, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> craftSessionService.create(USER_ID, PROJECT_ID, null));
        assertEquals(3001, ex.getCode());
    }

    @Test
    @DisplayName("暂停：DOING → PAUSED 成功")
    void testChangeStatus_Pause() {
        CraftSessionVO created = craftSessionService.create(USER_ID, PROJECT_ID, null);

        CraftSessionVO vo = craftSessionService.changeStatus(USER_ID, created.getSessionId(), "pause");

        assertEquals(2, vo.getStatus());
        assertEquals("已暂停", vo.getStatusLabel());
        assertNull(vo.getEndTime());
    }

    @Test
    @DisplayName("恢复：PAUSED → DOING 成功")
    void testChangeStatus_Resume() {
        CraftSessionVO created = craftSessionService.create(USER_ID, PROJECT_ID, null);
        craftSessionService.changeStatus(USER_ID, created.getSessionId(), "pause");

        CraftSessionVO vo = craftSessionService.changeStatus(USER_ID, created.getSessionId(), "resume");

        assertEquals(1, vo.getStatus());
        assertEquals("进行中", vo.getStatusLabel());
    }

    @Test
    @DisplayName("完成：DOING → FINISHED 成功，endTime 非空")
    void testChangeStatus_Finish() {
        CraftSessionVO created = craftSessionService.create(USER_ID, PROJECT_ID, null);

        CraftSessionVO vo = craftSessionService.changeStatus(USER_ID, created.getSessionId(), "finish");

        assertEquals(3, vo.getStatus());
        assertEquals("已完成", vo.getStatusLabel());
        assertNotNull(vo.getEndTime());
    }

    @Test
    @DisplayName("非法流转：FINISHED → pause → 3001")
    void testChangeStatus_IllegalTransition() {
        CraftSessionVO created = craftSessionService.create(USER_ID, PROJECT_ID, null);
        craftSessionService.changeStatus(USER_ID, created.getSessionId(), "finish");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> craftSessionService.changeStatus(USER_ID, created.getSessionId(), "pause"));
        assertEquals(3001, ex.getCode());
    }

    @Test
    @DisplayName("所有权校验：其他用户操作 → 1003")
    void testChangeStatus_NotOwner() {
        CraftSessionVO created = craftSessionService.create(USER_ID, PROJECT_ID, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> craftSessionService.changeStatus(OTHER_USER_ID, created.getSessionId(), "pause"));
        assertEquals(1003, ex.getCode());
    }

    @Test
    @DisplayName("查询进度：所有权校验 → 1003")
    void testGetProgress_NotOwner() {
        CraftSessionVO created = craftSessionService.create(USER_ID, PROJECT_ID, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> craftSessionService.getProgress(OTHER_USER_ID, created.getSessionId()));
        assertEquals(1003, ex.getCode());
    }

    // ──────────────────────────────────────────────────────
    //  辅助方法
    // ──────────────────────────────────────────────────────

    private void insertProject(Long id, String title, int status) {
        CraftProject p = new CraftProject();
        p.setId(id);
        p.setInheritorId(1L);
        p.setTitle(title);
        p.setIntro(title + " 简介");
        p.setLevel(1);
        p.setEstMinutes(30);
        p.setCategory("其他");
        p.setTherapyTags("专注,静心");
        p.setStatus(status);
        p.setCreateTime(LocalDateTime.now());
        p.setUpdateTime(LocalDateTime.now());
        p.setDeleted(0);
        craftProjectMapper.insert(p);
    }

    private void insertStep(Long projectId, int stepNo, String title) {
        CraftStep step = new CraftStep();
        step.setProjectId(projectId);
        step.setStepNo(stepNo);
        step.setTitle(title);
        step.setCreateTime(LocalDateTime.now());
        step.setUpdateTime(LocalDateTime.now());
        step.setDeleted(0);
        craftStepMapper.insert(step);
    }
}
