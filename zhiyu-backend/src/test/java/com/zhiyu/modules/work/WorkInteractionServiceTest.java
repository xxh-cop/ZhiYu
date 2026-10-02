package com.zhiyu.modules.work;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.work.entity.Work;
import com.zhiyu.modules.work.entity.WorkComment;
import com.zhiyu.modules.work.entity.WorkLike;
import com.zhiyu.modules.work.mapper.WorkCommentMapper;
import com.zhiyu.modules.work.mapper.WorkLikeMapper;
import com.zhiyu.modules.work.mapper.WorkMapper;
import com.zhiyu.modules.work.service.WorkInteractionService;
import com.zhiyu.modules.work.vo.CommentPageVO;
import com.zhiyu.modules.work.vo.LikeVO;
import com.zhiyu.modules.work.vo.CommentVO;
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
 * {@link WorkInteractionService} 集成测试（T1.8a）。
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class WorkInteractionServiceTest {

    @Autowired
    private WorkInteractionService workInteractionService;

    @Autowired
    private WorkMapper workMapper;

    @Autowired
    private WorkCommentMapper workCommentMapper;

    @Autowired
    private WorkLikeMapper workLikeMapper;

    @Autowired
    private UserMapper userMapper;

    private static final Long USER_ID = 8101L;
    private Long workId;

    @BeforeEach
    void setUp() {
        // 用户
        insertUser(USER_ID, "评论者");
        // 作品
        workId = insertWork(USER_ID, "测试作品");
    }

    @Test
    @DisplayName("首次点赞 → active=true, like_count+1")
    void testToggleLike_FirstTime() {
        LikeVO vo = workInteractionService.toggleLike(USER_ID, workId, 1);

        assertTrue(vo.getActive());
        Work work = workMapper.selectById(workId);
        assertEquals(1, work.getLikeCount());
    }

    @Test
    @DisplayName("再次点赞 → active=false, like_count-1")
    void testToggleLike_SecondTime() {
        workInteractionService.toggleLike(USER_ID, workId, 1);

        LikeVO vo = workInteractionService.toggleLike(USER_ID, workId, 1);

        assertFalse(vo.getActive());
        Work work = workMapper.selectById(workId);
        assertEquals(0, work.getLikeCount());
    }

    @Test
    @DisplayName("第三次点赞 → active=true（恢复 deleted=1 的记录）")
    void testToggleLike_ThirdTime() {
        workInteractionService.toggleLike(USER_ID, workId, 1); // +1
        workInteractionService.toggleLike(USER_ID, workId, 1); // -1

        LikeVO vo = workInteractionService.toggleLike(USER_ID, workId, 1); // +1

        assertTrue(vo.getActive());
        Work work = workMapper.selectById(workId);
        assertEquals(1, work.getLikeCount());
    }

    @Test
    @DisplayName("点赞 type=1 后收藏 type=0 仍可成功")
    void testToggleLike_CollectIndependent() {
        workInteractionService.toggleLike(USER_ID, workId, 1); // 点赞

        LikeVO vo = workInteractionService.toggleLike(USER_ID, workId, 0); // 收藏

        assertTrue(vo.getActive());
    }

    @Test
    @DisplayName("评论成功 → 落库")
    void testComment_Success() {
        CommentVO vo = workInteractionService.comment(USER_ID, workId, "很棒的作品！");

        assertNotNull(vo.getId());
        assertEquals("很棒的作品！", vo.getContent());
        assertEquals("评论者", vo.getAuthorName());

        // 确认落库
        Long count = workCommentMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkComment>()
                        .eq(WorkComment::getWorkId, workId));
        assertEquals(1, count.intValue());
    }

    @Test
    @DisplayName("评论命中高危词 → 抛 BusinessException(1001)")
    void testComment_Blocked() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> workInteractionService.comment(USER_ID, workId, "我想自杀"));

        assertEquals(1001, ex.getCode());
    }

    @Test
    @DisplayName("评论列表分页正确")
    void testListComments_Pagination() {
        for (int i = 0; i < 5; i++) {
            insertComment(workId, USER_ID, "评论" + i);
        }

        CommentPageVO result = workInteractionService.listComments(workId, 1, 2);

        assertEquals(5, result.getTotal());
        assertEquals(2, result.getRecords().size());
    }

    @Test
    @DisplayName("评论列表：作品不存在 → 1004")
    void testListComments_WorkNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> workInteractionService.listComments(99999L, 1, 10));

        assertEquals(1004, ex.getCode());
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

    private Long insertWork(Long userId, String title) {
        Work w = new Work();
        w.setUserId(userId);
        w.setTitle(title);
        w.setVisible(1);
        w.setAnonymous(1);
        w.setLikeCount(0);
        w.setStatus(1);
        w.setCreateTime(LocalDateTime.now());
        w.setUpdateTime(LocalDateTime.now());
        w.setDeleted(0);
        workMapper.insert(w);
        return w.getId();
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
