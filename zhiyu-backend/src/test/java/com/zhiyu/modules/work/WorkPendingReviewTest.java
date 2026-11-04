package com.zhiyu.modules.work;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.work.dto.WorkPublishReq;
import com.zhiyu.modules.work.entity.Work;
import com.zhiyu.modules.work.mapper.WorkMapper;
import com.zhiyu.modules.work.service.WorkInteractionService;
import com.zhiyu.modules.work.service.WorkService;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * N1 图文作品「先审后发」H2 单测（2026-11-04）。
 *
 * <p>覆盖：新发布一律 status=0、作者可见、他人 1004、互动拒他人/提示作者、
 * 审核通过后公开进入作品流、历史 status=1 数据不回滚。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("N1 先审后发：图文作品发布即待审")
class WorkPendingReviewTest {

    private static final long AUTHOR = 995001L;
    private static final long OTHER = 995002L;

    @Autowired WorkService workService;
    @Autowired WorkInteractionService interactions;
    @Autowired WorkMapper workMapper;

    @AfterEach
    void cleanup() {
        UserContext.clear();
    }

    private void actor(long id) {
        UserContext.set(new LoginUser(id, 0));
    }

    private Long publishText(String title) {
        actor(AUTHOR);
        WorkPublishReq req = new WorkPublishReq();
        req.setTitle(title);
        req.setMediaType("text");
        Long id = workService.publish(AUTHOR, req).getId();
        UserContext.clear();
        return id;
    }

    @Test
    @DisplayName("新发布 text 作品 status=0（先审后发），VO 携带 status")
    void publishGoesPending() {
        Long id = publishText("先审后发的图文");
        Work work = workMapper.selectById(id);
        assertThat(work.getStatus()).isEqualTo(0);
        actor(AUTHOR);
        assertThat(workService.getWork(AUTHOR, id).getStatus()).isEqualTo(0);
    }

    @Test
    @DisplayName("作者可见本人待审作品；他人访问返回 1004（不泄露存在性）")
    void authorSeesPendingOthersGet1004() {
        Long id = publishText("作者可见待审");
        actor(AUTHOR);
        assertThat(workService.getWork(AUTHOR, id)).isNotNull();
        actor(OTHER);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> workService.getWork(OTHER, id));
        assertThat(ex.getCode()).isEqualTo(1004);
    }

    @Test
    @DisplayName("互动语义：他人对待审互动 1004；作者对本人待审写互动 3001；作者可读评论")
    void interactionSemantics() {
        Long id = publishText("互动语义作品");
        actor(OTHER);
        assertThat(assertThrows(BusinessException.class,
                () -> interactions.comment(OTHER, id, "你好")).getCode()).isEqualTo(1004);
        assertThat(assertThrows(BusinessException.class,
                () -> interactions.toggleLike(OTHER, id, 1)).getCode()).isEqualTo(1004);

        actor(AUTHOR);
        assertThat(assertThrows(BusinessException.class,
                () -> interactions.comment(AUTHOR, id, "自言自语")).getCode()).isEqualTo(3001);
        assertThat(assertThrows(BusinessException.class,
                () -> interactions.toggleLike(AUTHOR, id, 1)).getCode()).isEqualTo(3001);
        // 读评论（write=false）：作者放行
        assertThat(interactions.listComments(id, 1, 10).getTotal()).isEqualTo(0);
    }

    @Test
    @DisplayName("审核通过后公开：进入作品流、他人可见、互动恢复")
    void approvedThenPublic() {
        Long id = publishText("审核通过作品");
        workMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<Work>lambdaUpdate().eq(Work::getId, id).set(Work::getStatus, 1));

        actor(OTHER);
        assertThat(workService.getWork(OTHER, id)).isNotNull();
        assertThat(workService.listWorks(1, 10).getRecords().stream()
                .anyMatch(w -> w.getId().equals(id))).isTrue();
        interactions.toggleLike(OTHER, id, 1);
        Work refreshed = workMapper.selectById(id);
        assertThat(refreshed.getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("历史 status=1 数据不回滚：仍在作品流中返回")
    void historicalPublicDataUntouched() {
        // 模拟 2026-11-04 前已公开的历史作品（直接置 status=1，不走新发布流程）
        Work legacy = new Work();
        legacy.setUserId(AUTHOR);
        legacy.setTitle("历史公开作品");
        legacy.setMediaType("text");
        legacy.setVisible(1);
        legacy.setAnonymous(1);
        legacy.setLikeCount(0);
        legacy.setStatus(1);
        workMapper.insert(legacy);

        actor(OTHER);
        assertThat(workService.listWorks(1, 10).getRecords().stream()
                .anyMatch(w -> w.getId().equals(legacy.getId()))).isTrue();
        // 且新发布不再出现在列表（未审）
        Long pendingId = publishText("新待审作品");
        actor(OTHER);
        assertThat(workService.listWorks(1, 10).getRecords().stream()
                .anyMatch(w -> w.getId().equals(pendingId))).isFalse();
    }
}
