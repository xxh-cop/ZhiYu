package com.zhiyu.modules.craft;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.entity.CraftStep;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.craft.mapper.CraftStepMapper;
import com.zhiyu.modules.craft.service.CraftProjectService;
import com.zhiyu.modules.craft.vo.CraftStepVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CraftProjectService} 集成测试（T1.9b-backend）。
 *
 * <p>@SpringBootTest + H2，不依赖 init-data.sql。
 * 用 @DirtiesContext(AFTER_CLASS) 避免数据污染。
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class CraftProjectServiceTest {

    @Autowired
    private CraftProjectService craftProjectService;

    @Autowired
    private CraftProjectMapper craftProjectMapper;

    @Autowired
    private CraftStepMapper craftStepMapper;

    private static final Long PROJECT_ID = 9301L;
    private static final Long UNPUBLISHED_PROJECT_ID = 9302L;

    @BeforeEach
    void setUp() {
        insertProject(PROJECT_ID, "编织测试", 1);
        insertProject(UNPUBLISHED_PROJECT_ID, "未上架项目", 0);

        // 插入 3 个步骤（故意乱序插入，验证按 stepNo 升序返回）
        insertStep(PROJECT_ID, 3, "收尾整理", "慢慢收束线头，感受完成");
        insertStep(PROJECT_ID, 1, "准备材料", "选好棉线和棒针");
        insertStep(PROJECT_ID, 2, "开始编织", "起针后按图样织");
    }

    @Test
    @DisplayName("listSteps 成功：返回 3 个步骤，按 stepNo 升序；上架项目非只读")
    void testListSteps_Success() {
        var view = craftProjectService.listSteps(PROJECT_ID);
        List<CraftStepVO> steps = view.getSteps();

        assertEquals(3, steps.size());
        // 验证按 stepNo 升序
        assertEquals(1, steps.get(0).getStepNo());
        assertEquals("准备材料", steps.get(0).getTitle());
        assertEquals("选好棉线和棒针", steps.get(0).getGuide());

        assertEquals(2, steps.get(1).getStepNo());
        assertEquals("开始编织", steps.get(1).getTitle());

        assertEquals(3, steps.get(2).getStepNo());
        assertEquals("收尾整理", steps.get(2).getTitle());

        // 上架项目：非只读、无提示语
        assertEquals(1, view.getProjectStatus());
        assertFalse(view.getReadOnly());
        assertTrue(view.getCanReview());
        assertFalse(view.getCanExport());
        assertNull(view.getNoticeKey());
    }

    @Test
    @DisplayName("listSteps 项目不存在 → 1004")
    void testListSteps_ProjectNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> craftProjectService.listSteps(9999L));
        assertEquals(1004, ex.getCode());
    }

    @Test
    @DisplayName("listSteps 项目待审（status=0）→ 1004（不泄露存在性；下架/归档走结构化视图，见分层下架测试）")
    void testListSteps_Unpublished() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> craftProjectService.listSteps(UNPUBLISHED_PROJECT_ID));
        assertEquals(1004, ex.getCode());
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

    private void insertStep(Long projectId, int stepNo, String title, String guide) {
        CraftStep step = new CraftStep();
        step.setProjectId(projectId);
        step.setStepNo(stepNo);
        step.setTitle(title);
        step.setGuide(guide);
        step.setCreateTime(LocalDateTime.now());
        step.setUpdateTime(LocalDateTime.now());
        step.setDeleted(0);
        craftStepMapper.insert(step);
    }
}
