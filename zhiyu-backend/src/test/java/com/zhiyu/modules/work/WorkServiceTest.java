package com.zhiyu.modules.work;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.entity.CraftSession;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.craft.mapper.CraftSessionMapper;
import com.zhiyu.modules.work.dto.WorkPublishReq;
import com.zhiyu.modules.work.entity.Work;
import com.zhiyu.modules.work.entity.WorkComment;
import com.zhiyu.modules.work.mapper.WorkCommentMapper;
import com.zhiyu.modules.work.mapper.WorkMapper;
import com.zhiyu.modules.work.service.WorkService;
import com.zhiyu.modules.work.vo.WorkPageVO;
import com.zhiyu.modules.work.vo.WorkVO;
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
 * {@link WorkService} 集成测试（T1.8a）。
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class WorkServiceTest {

    @Autowired
    private WorkService workService;

    @Autowired
    private WorkMapper workMapper;

    @Autowired
    private WorkCommentMapper workCommentMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CraftSessionMapper craftSessionMapper;

    @Autowired
    private CraftProjectMapper craftProjectMapper;

    private static final Long USER_ID = 8001L;
    private static final Long PROJECT_ID = 9301L;
    private static final Long SESSION_ID = 9302L;

    @BeforeEach
    void setUp() {
        // 用户
        insertUser(USER_ID, "小织");
        // 项目
        insertProject(PROJECT_ID, "/static/covers/test.jpg");
        // 会话
        insertSession(SESSION_ID, USER_ID, PROJECT_ID);
    }

    @Test
    @DisplayName("发布：只传 title → 成功，visible=1, anonymous=true")
    void testPublish_MinimalFields() {
        WorkPublishReq req = new WorkPublishReq();
        req.setTitle("测试作品");
        req.setMediaType("text");

        WorkVO vo = workService.publish(USER_ID, req);

        assertNotNull(vo.getId());
        assertEquals("测试作品", vo.getTitle());
        assertEquals(1, vo.getVisible());
        assertTrue(vo.getAnonymous());
        assertEquals(0, vo.getLikeCount());
        assertEquals(0, vo.getCommentCount());
    }

    @Test
    @DisplayName("发布：传 sessionId → coverUrl 继承")
    void testPublish_WithSessionInheritCover() {
        WorkPublishReq req = new WorkPublishReq();
        req.setTitle("从会话创建");
        req.setSessionId(SESSION_ID);
        req.setMediaType("text");

        WorkVO vo = workService.publish(USER_ID, req);

        assertEquals("/static/covers/test.jpg", vo.getCoverUrl());
    }

    @Test
    @DisplayName("匿名作品 → authorName=匿名同学")
    void testPublish_AnonymousAuthorName() {
        WorkPublishReq req = new WorkPublishReq();
        req.setTitle("匿名作品");
        req.setAnonymous(true);
        req.setMediaType("text");

        WorkVO vo = workService.publish(USER_ID, req);

        assertEquals("匿名同学", vo.getAuthorName());
    }

    @Test
    @DisplayName("不传 message → AI 生成寄语，落库非空")
    void testPublish_MessageGenerated() {
        WorkPublishReq req = new WorkPublishReq();
        req.setTitle("无寄语作品");
        req.setMediaType("text");

        WorkVO vo = workService.publish(USER_ID, req);

        assertNotNull(vo.getMessage());
        assertFalse(vo.getMessage().isBlank());
    }

    @Test
    @DisplayName("传 message → 落库的是自定义寄语")
    void testPublish_MessageFromReq() {
        WorkPublishReq req = new WorkPublishReq();
        req.setTitle("自定义寄语作品");
        req.setMessage("自定义寄语");
        req.setMediaType("text");

        WorkVO vo = workService.publish(USER_ID, req);

        assertEquals("自定义寄语", vo.getMessage());
    }

    @Test
    @DisplayName("非匿名作品 → authorName=昵称")
    void testPublish_NonAnonymousAuthorName() {
        WorkPublishReq req = new WorkPublishReq();
        req.setTitle("实名作品");
        req.setAnonymous(false);
        req.setMediaType("text");

        WorkVO vo = workService.publish(USER_ID, req);

        assertEquals("小织", vo.getAuthorName());
    }

    @Test
    @DisplayName("作品流：visible=0 或 status=0 的作品不返回")
    void testListWorks_FiltersInvisible() {
        insertWork(USER_ID, "公开作品", 1, 1, 1);   // visible=1, status=1 → 返回
        insertWork(USER_ID, "私密作品", 1, 0, 1);   // visible=0 → 不返回
        insertWork(USER_ID, "待审作品", 1, 1, 0);   // status=0 → 不返回

        WorkPageVO result = workService.listWorks(1, 10);

        assertEquals(1, result.getRecords().size());
        assertEquals("公开作品", result.getRecords().get(0).getTitle());
    }

    @Test
    @DisplayName("分页：page=1 size=2 → 返回 2 条 + total")
    void testListWorks_Pagination() {
        for (int i = 0; i < 5; i++) {
            insertWork(USER_ID, "作品" + i, 1, 1, 1);
        }

        WorkPageVO result = workService.listWorks(1, 2);

        assertEquals(5, result.getTotal());
        assertEquals(2, result.getRecords().size());
    }

    @Test
    @DisplayName("commentCount：作品有 2 条评论 → commentCount=2")
    void testListWorks_CommentCount() {
        WorkPublishReq req = new WorkPublishReq();
        req.setTitle("评论测试");
        req.setMediaType("text");
        WorkVO work = workService.publish(USER_ID, req);
        // N1（2026-11-04 先审后发）：新发布为待审，模拟审核通过转公开后进入作品流
        workMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<Work>lambdaUpdate()
                .eq(Work::getId, work.getId()).set(Work::getStatus, 1));
        insertComment(work.getId(), USER_ID, "评论1");
        insertComment(work.getId(), USER_ID, "评论2");

        WorkPageVO result = workService.listWorks(1, 10);

        WorkVO vo = result.getRecords().stream()
                .filter(w -> w.getId().equals(work.getId()))
                .findFirst().orElse(null);
        assertNotNull(vo);
        assertEquals(2, vo.getCommentCount());
    }

    @Test
    @DisplayName("size=100 → 实际取 50")
    void testListWorks_SizeCapAt50() {
        WorkPageVO result = workService.listWorks(1, 100);

        assertEquals(50, result.getSize());
    }

    // ── T1.9c-backend: getWork ──

    @Test
    @DisplayName("getWork：公开作品 → 返回 WorkVO")
    void testGetWork_Success() {
        insertWork(USER_ID, "公开作品", 3, 1, 1); // visible=1, status=1

        // 取刚插入的 work id
        Work inserted = workMapper.selectList(null).stream()
                .filter(w -> "公开作品".equals(w.getTitle()))
                .findFirst().orElse(null);
        assertNotNull(inserted);

        WorkVO vo = workService.getWork(USER_ID, inserted.getId());

        assertEquals("公开作品", vo.getTitle());
        assertEquals(3, vo.getLikeCount());
        assertEquals("匿名同学", vo.getAuthorName()); // insertWork 设 anonymous=1
    }

    @Test
    @DisplayName("getWork：id 不存在 → 1004")
    void testGetWork_NotFound() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> workService.getWork(USER_ID, 99999999L)
        );
        assertEquals(1004, ex.getCode());
    }

    @Test
    @DisplayName("getWork：私密作品被其他用户查看 → 1004")
    void testGetWork_PrivateByOther() {
        Long otherUserId = 8002L;
        insertUser(otherUserId, "其他用户");
        insertWork(otherUserId, "私密作品", 0, 0, 1); // visible=0

        Work privateWork = workMapper.selectList(null).stream()
                .filter(w -> "私密作品".equals(w.getTitle()))
                .findFirst().orElse(null);
        assertNotNull(privateWork);

        // USER_ID 不是作者，查看私密作品应 1004
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> workService.getWork(USER_ID, privateWork.getId())
        );
        assertEquals(1004, ex.getCode());
    }

    // ── AE2（2026-11-04）：getWork 的 auditReason 可见性 ──

    @Test
    @DisplayName("getWork：作者本人查看被下架作品 → auditReason 下发")
    void testGetWork_AuditReasonVisibleToAuthor() {
        insertWork(USER_ID, "被下架作品", 0, 1, 2);
        Work w = workMapper.selectList(null).stream()
                .filter(x -> "被下架作品".equals(x.getTitle())).findFirst().orElseThrow();
        w.setAuditReason("封面不符合社区规范");
        workMapper.updateById(w);

        WorkVO vo = workService.getWork(USER_ID, w.getId());

        assertEquals("封面不符合社区规范", vo.getAuditReason());
    }

    @Test
    @DisplayName("getWork：他人查看被下架作品 → 1004（auditReason 不下发的前提）")
    void testGetWork_RejectedWorkInvisibleToOthers() {
        Long otherUserId = 8003L;
        insertUser(otherUserId, "他人");
        insertWork(otherUserId, "他人下架作品", 0, 1, 2);
        Work w = workMapper.selectList(null).stream()
                .filter(x -> "他人下架作品".equals(x.getTitle())).findFirst().orElseThrow();
        w.setAuditReason("违规内容");
        workMapper.updateById(w);

        Work target = w;
        BusinessException ex = assertThrows(BusinessException.class,
                () -> workService.getWork(USER_ID, target.getId()));
        assertEquals(1004, ex.getCode());
    }

    @Test
    @DisplayName("getWork：作者查看已通过作品 → auditReason 为 null")
    void testGetWork_AuditReasonNullWhenApproved() {
        insertWork(USER_ID, "已通过作品", 0, 1, 1);
        Work w = workMapper.selectList(null).stream()
                .filter(x -> "已通过作品".equals(x.getTitle())).findFirst().orElseThrow();

        WorkVO vo = workService.getWork(USER_ID, w.getId());

        assertEquals(1, vo.getStatus());
        assertNull(vo.getAuditReason());
    }

    // ── AE3（2026-11-04）：listWorks 的 total 口径 ──

    @Test
    @DisplayName("listWorks：total 为 Integer（序列化 JSON number，与 PageVO 口径一致）")
    void testListWorks_TotalIsInteger() {
        insertWork(USER_ID, "计数口径作品", 0, 1, 1);
        WorkPageVO result = workService.listWorks(1, 10);
        // 编译期类型即 Integer；运行期再防一次意外改回 Long（JacksonConfig 会把它序列化成字符串）
        assertEquals(Integer.class, ((Object) result.getTotal()).getClass());
    }

    // ──────────────────────────────────────────────────────
    //  辅助方法
    // ──────────────────────────────────────────────────────

    private void insertUser(Long id, String nickname) {
        User u = new User();
        u.setId(id);
        u.setUsername("test_" + id);
        u.setPassword("$2a$10$dummy");
        u.setNickname(nickname);
        u.setRole(0);
        u.setStatus(1);
        u.setCanContact(0);
        u.setCreateTime(LocalDateTime.now());
        u.setUpdateTime(LocalDateTime.now());
        u.setDeleted(0);
        userMapper.insert(u);
    }

    private void insertProject(Long id, String coverUrl) {
        CraftProject p = new CraftProject();
        p.setId(id);
        p.setInheritorId(1L);
        p.setTitle("测试项目");
        p.setLevel(1);
        p.setEstMinutes(30);
        p.setCategory("其他");
        p.setTherapyTags("专注");
        p.setCoverUrl(coverUrl);
        p.setStatus(1);
        p.setCreateTime(LocalDateTime.now());
        p.setUpdateTime(LocalDateTime.now());
        p.setDeleted(0);
        craftProjectMapper.insert(p);
    }

    private void insertSession(Long id, Long userId, Long projectId) {
        CraftSession s = new CraftSession();
        s.setId(id);
        s.setUserId(userId);
        s.setProjectId(projectId);
        s.setStatus(1);
        s.setCurrentStep(1);
        s.setStartTime(LocalDateTime.now());
        s.setFlowSeconds(0);
        s.setCreateTime(LocalDateTime.now());
        s.setUpdateTime(LocalDateTime.now());
        s.setDeleted(0);
        craftSessionMapper.insert(s);
    }

    private void insertWork(Long userId, String title, int likeCount, int visible, int status) {
        Work w = new Work();
        w.setUserId(userId);
        w.setTitle(title);
        w.setVisible(visible);
        w.setAnonymous(1);
        w.setLikeCount(likeCount);
        w.setStatus(status);
        w.setMediaType("image");
        w.setCreateTime(LocalDateTime.now());
        w.setUpdateTime(LocalDateTime.now());
        w.setDeleted(0);
        workMapper.insert(w);
    }

    private void insertComment(Long workId, Long userId, String content) {
        WorkComment c = new WorkComment();
        c.setWorkId(workId);
        c.setUserId(userId);
        c.setContent(content);
        c.setStatus(1);
        c.setCreateTime(LocalDateTime.now());
        c.setUpdateTime(LocalDateTime.now());
        c.setDeleted(0);
        workCommentMapper.insert(c);
    }
}
