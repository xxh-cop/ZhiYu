package com.zhiyu.security;

import com.zhiyu.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Rechecks revocation and ownership at business boundaries, including async jobs. */
@Service
@RequiredArgsConstructor
public class BusinessAccess {
    private final JdbcTemplate jdbc;
    public Long user() {
        Long id = UserContext.getUserId();
        if(id!=null && org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())jdbc.queryForList("SELECT id FROM user WHERE id=? FOR UPDATE",id);
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
