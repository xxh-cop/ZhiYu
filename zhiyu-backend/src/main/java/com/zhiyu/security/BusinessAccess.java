package com.zhiyu.security;

import com.zhiyu.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * Rechecks revocation and ownership at business boundaries, including async jobs.
 *
 * <p><b>并发取舍（2026-10-26）</b>：本方法原来在事务内无条件
 * {@code SELECT id FROM user ... FOR UPDATE}，意图是让“注销/禁用”事务与正在执行的业务事务
 * 通过 user 行锁互斥。代价是同用户的并发请求在事务内全部串行、热点行锁放大死锁概率，
 * 而实际收益有限：注销清理并非只依赖这一把锁（{@link com.zhiyu.modules.auth.service.AccountLifecycle}
 * 自己会对 user 行加锁并逐表清理）。
 *
 * <p>现在改为普通 {@code SELECT} 复核 {@code status=1 AND deleted=0}，不加锁。
 * <ul>
 *   <li>已经注销/禁用的用户：复核直接 1002，请求被拒（不变）。</li>
 *   <li>复核通过后、业务写库前正好完成注销：该请求可能写入归属已注销用户的数据。
 *       这些数据仍按 user_id 归属隔离，不会泄露给其他用户；由后续注销清理/人工清理兜底。
 *       高敏感路径（如 SSE 对话）会在写回复前再调一次 {@code consentedUser()} 缩小窗口。</li>
 *   <li>注销事务本身仍通过 {@code AccountLifecycle} 的 user 行锁 + status=0 保证
 *       “注销之后新到达的请求”一律被拒。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class BusinessAccess {
    private final JdbcTemplate jdbc;
    public Long user() {
        Long id = UserContext.getUserId();
        if (id == null || jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND status=1 AND deleted=0", Integer.class, id) == 0)
            throw new BusinessException(1002, "登录已失效");
        return id;
    }
    public Long consentedUser() {
        Long id = user();
        if (jdbc.queryForObject("SELECT privacy_agreed FROM user WHERE id=?", Integer.class, id) != 1)
            throw new BusinessException(1003, "请先阅读并同意隐私政策");
        return id;
    }
    public Long role(Integer... roles) {
        Long id = user();
        Integer current = jdbc.queryForObject("SELECT role FROM user WHERE id=?", Integer.class, id);
        if (!Arrays.asList(roles).contains(current)) throw new BusinessException(1003, "无权执行此操作");
        return id;
    }
    public void emotionSession(Long id, Long userId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM emotion_session WHERE id=? AND user_id=? AND deleted=0", Integer.class, id, userId) == 0)
            throw new BusinessException(1004, "会话不存在");
    }
}
