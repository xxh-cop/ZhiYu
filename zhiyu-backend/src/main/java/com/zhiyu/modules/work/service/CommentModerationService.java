package com.zhiyu.modules.work.service;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.care.OperationLog;
import com.zhiyu.security.BusinessAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Service @RequiredArgsConstructor
public class CommentModerationService {
    private final JdbcTemplate db;
    private final BusinessAccess access;
    private final OperationLog log;
    public Map<String,Object> list(int page,int status){
        access.role(3);
        if(page<1||page>100000||status<0||status>2)throw new BusinessException(1001,"分页或状态无效");
        var rows=db.queryForList("SELECT CAST(c.id AS CHAR) id,c.content,c.status,c.create_time,w.title workTitle,COALESCE(u.nickname,'已注销用户') nickname FROM work_comment c LEFT JOIN work w ON w.id=c.work_id LEFT JOIN user u ON u.id=c.user_id AND u.deleted=0 WHERE c.deleted=0 AND c.status=? ORDER BY c.create_time DESC,c.id DESC LIMIT 20 OFFSET ?",status,(page-1)*20);
        return Map.of("records",rows,"total",db.queryForObject("SELECT COUNT(*) FROM work_comment WHERE deleted=0 AND status=?",Long.class,status));
    }
    @Transactional public void review(long id,int status,int expected){
        access.role(3);
        if((status!=1&&status!=2)||expected<0||expected>2||status==expected)throw new BusinessException(1001,"请选择有效审核操作");
        var rows=db.queryForList("SELECT work_id FROM work_comment WHERE id=? AND deleted=0",id);
        if(rows.isEmpty())throw new BusinessException(1004,"评论已不存在");
        var works=db.queryForList("SELECT status,deleted FROM work WHERE id=? FOR UPDATE",rows.get(0).get("work_id"));
        if(status==1&&(works.isEmpty()||((Number)works.get(0).get("status")).intValue()!=1||((Number)works.get(0).get("deleted")).intValue()!=0))throw new BusinessException(3001,"作品已下架或删除，不能公开评论");
        int changed=db.update("UPDATE work_comment SET status=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND status=? AND deleted=0",status,id,expected);
        if(changed!=1)throw new BusinessException(3001,"评论状态已变化，请刷新后处理");
        log.record(status==1?"COMMENT_APPROVE":"COMMENT_HIDE",id);
    }
    @Transactional public void remove(long workId,long id){
        long uid=access.user();
        var rows=db.queryForList("SELECT user_id FROM work_comment WHERE id=? AND work_id=? AND deleted=0 FOR UPDATE",id,workId);
        if(rows.isEmpty())throw new BusinessException(1004,"评论已不存在");
        if(((Number)rows.get(0).get("user_id")).longValue()!=uid)throw new BusinessException(1003,"只能撤回自己的评论");
        db.update("UPDATE work_comment SET content='',deleted=1,update_time=CURRENT_TIMESTAMP WHERE id=?",id);
    }
}
