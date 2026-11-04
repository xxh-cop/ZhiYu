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
    private final com.zhiyu.modules.notification.service.NotificationService notificationService;
    public Map<String,Object> list(int page,int status){return list(page,status,null);}
    /** 评论列表（O2 2026-11-04 新增可选 workId 过滤；管理端视角不受学生端 WorkAudience 限制）。 */
    public Map<String,Object> list(int page,int status,Long workId){
        access.role(3);
        if(page<1||page>100000||status<0||status>2)throw new BusinessException(1001,"分页或状态无效");
        String workFilter=workId==null?"":" AND c.work_id=?";
        Object[] args=workId==null?new Object[]{status,(page-1)*20}:new Object[]{status,workId,(page-1)*20};
        var rows=db.queryForList("SELECT CAST(c.id AS CHAR) id,c.content,c.status,c.create_time,w.title workTitle,COALESCE(u.nickname,'已注销用户') nickname FROM work_comment c LEFT JOIN work w ON w.id=c.work_id LEFT JOIN user u ON u.id=c.user_id AND u.deleted=0 WHERE c.deleted=0 AND c.status=?"+workFilter+" ORDER BY c.create_time DESC,c.id DESC LIMIT 20 OFFSET ?",args);
        Object[] countArgs=workId==null?new Object[]{status}:new Object[]{status,workId};
        Long total=db.queryForObject("SELECT COUNT(*) FROM work_comment WHERE deleted=0 AND status=?"+(workId==null?"":" AND work_id=?"),Long.class,countArgs);
        // S3（2026-11-04）：分页元数据统一为 JSON number（雪花 ID 仍按字符串）
        return Map.of("records",rows,"total",total==null?0:total.intValue());
    }
    /**
     * 评论详情（O2 2026-11-04）：role∈{2,3} 可读脱敏摘要。
     * 只返回 workId/状态/作者昵称/内容截断（≤50 字），不返回全文（通知跳转定位用）。
     * 已删除评论返回 1004。
     */
    public Map<String,Object> detail(long id){
        access.role(2,3);
        var rows=db.queryForList("SELECT c.id,c.work_id,c.status,c.content,c.create_time,COALESCE(u.nickname,'已注销用户') nickname FROM work_comment c LEFT JOIN user u ON u.id=c.user_id AND u.deleted=0 WHERE c.id=? AND c.deleted=0",id);
        if(rows.isEmpty())throw new BusinessException(1004,"评论不存在或已删除");
        var row=rows.get(0);
        String content=java.util.Objects.toString(row.get("content"),"");
        return Map.of(
                "id",String.valueOf(((Number)row.get("id")).longValue()),
                "workId",String.valueOf(((Number)row.get("work_id")).longValue()),
                "status",row.get("status"),
                "authorName",row.get("nickname"),
                "contentExcerpt",content.length()<=50?content:content.substring(0,50)+"…",
                "createTime",row.get("create_time").toString());
    }
    @Transactional public void review(long id,int status,int expected,String reason){
        access.role(3);
        if((status!=1&&status!=2)||expected<0||expected>2||status==expected)throw new BusinessException(1001,"请选择有效审核操作");
        // I1（2026-11-04）：隐藏/驳回必须携带理由（落库并随通知送达作者）
        if(status==2&&(reason==null||reason.isBlank()))throw new BusinessException(1001,"隐藏评论必须填写理由");
        var rows=db.queryForList("SELECT work_id,user_id FROM work_comment WHERE id=? AND deleted=0",id);
        if(rows.isEmpty())throw new BusinessException(1004,"评论已不存在");
        var works=db.queryForList("SELECT status,deleted FROM work WHERE id=? FOR UPDATE",rows.get(0).get("work_id"));
        if(status==1&&(works.isEmpty()||((Number)works.get(0).get("status")).intValue()!=1||((Number)works.get(0).get("deleted")).intValue()!=0))throw new BusinessException(3001,"作品已下架或删除，不能公开评论");
        int changed=db.update("UPDATE work_comment SET status=?,audit_reason=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND status=? AND deleted=0",status,reason,id,expected);
        if(changed!=1)throw new BusinessException(3001,"评论状态已变化，请刷新后处理");
        log.record(status==1?"COMMENT_APPROVE":"COMMENT_HIDE",id);
        // 通知系统（2026-11-04）：审核结果（含理由）触达评论作者；失败不阻断审核主流程
        // S2（2026-11-04）：通知 ref_id=所属作品（跳转目标），sub_ref_id=评论锚点
        try{notificationService.notifyCommentAudit(((Number)rows.get(0).get("user_id")).longValue(),id,((Number)rows.get(0).get("work_id")).longValue(),status,reason);}
        catch(Exception e){org.slf4j.LoggerFactory.getLogger(CommentModerationService.class).warn("评论审核通知写入失败（不阻断审核）: commentId={}, err={}",id,e.getMessage());}
    }
    @Transactional public void remove(long workId,long id){
        long uid=access.user();
        var rows=db.queryForList("SELECT user_id FROM work_comment WHERE id=? AND work_id=? AND deleted=0 FOR UPDATE",id,workId);
        if(rows.isEmpty())throw new BusinessException(1004,"评论已不存在");
        if(((Number)rows.get(0).get("user_id")).longValue()!=uid)throw new BusinessException(1003,"只能撤回自己的评论");
        db.update("UPDATE work_comment SET content='',deleted=1,update_time=CURRENT_TIMESTAMP WHERE id=?",id);
    }
}
