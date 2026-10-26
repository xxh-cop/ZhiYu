package com.zhiyu.modules.group.service;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.BusinessAccess;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.ai.safety.SafetyAction;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class GroupCommunityService {
    private final JdbcTemplate db;
    private final BusinessAccess access;
    private final AesEncryptor crypto;
    private final ContentSafetyFilter safety;
    private Map<String,Object> group(long id,boolean lock){var rows=db.queryForList("SELECT * FROM support_group WHERE id=? AND deleted=0"+(lock?" FOR UPDATE":""),id);if(rows.isEmpty())throw new BusinessException(1004,"小组不存在");return rows.get(0);}
    private Map<String,Object> member(long id,long uid){var rows=db.queryForList("SELECT id,role FROM group_member WHERE group_id=? AND user_id=? AND deleted=0",id,uid);if(rows.isEmpty())throw new BusinessException(1003,"仅小组成员可访问");return rows.get(0);}
    public Map<String,Object> detail(long id){long uid=access.user();var g=group(id,false);var m=member(id,uid);g.put("id",String.valueOf(id));g.put("myRole",m.get("role"));g.put("myMemberId",m.get("id").toString());g.put("members",db.queryForList("SELECT TRIM(CAST(m.id AS CHAR(20))) id,m.role,u.nickname FROM group_member m JOIN user u ON u.id=m.user_id AND u.deleted=0 AND u.status=1 WHERE m.group_id=? AND m.deleted=0 ORDER BY m.id",id));return g;}
    @Transactional public void leave(long id){long uid=access.user();group(id,true);var m=member(id,uid);int count=db.queryForObject("SELECT COUNT(*) FROM group_member WHERE group_id=? AND deleted=0",Integer.class,id);if("组长".equals(m.get("role"))&&count>1)throw new BusinessException(3001,"请先将组长转让给其他成员");db.update("UPDATE group_companion SET state='CANCELLED',update_time=CURRENT_TIMESTAMP WHERE group_id=? AND (first_user_id=? OR second_user_id=?)",id,uid,uid);db.update("UPDATE group_member SET deleted=1,update_time=CURRENT_TIMESTAMP WHERE id=?",m.get("id"));db.update("UPDATE support_group SET member_count=?,status=CASE WHEN ?=0 THEN 0 ELSE status END,update_time=CURRENT_TIMESTAMP WHERE id=?",count-1,count-1,id);}
    @Transactional public void transfer(long id,long memberId){long uid=access.user();var g=group(id,true);var me=member(id,uid);if(!"组长".equals(me.get("role")))throw new BusinessException(1003,"仅组长可转让");if(((Number)g.get("status")).intValue()!=1)throw new BusinessException(3001,"小组已关闭");var candidates=db.queryForList("SELECT m.id,m.user_id FROM group_member m JOIN user u ON u.id=m.user_id AND u.deleted=0 AND u.status=1 WHERE m.id=? AND m.group_id=? AND m.deleted=0",memberId,id);if(candidates.isEmpty()||((Number)candidates.get(0).get("user_id")).longValue()==uid)throw new BusinessException(1001,"请选择另一名有效成员");db.update("UPDATE group_member SET role='成员',update_time=CURRENT_TIMESTAMP WHERE id=?",me.get("id"));db.update("UPDATE group_member SET role='组长',update_time=CURRENT_TIMESTAMP WHERE id=?",memberId);}
    public List<Map<String,Object>> messages(long id,Long before){long uid=access.user();group(id,false);member(id,uid);var rows=db.queryForList("SELECT m.id,m.user_id,m.content,m.create_time,u.nickname FROM group_message m LEFT JOIN user u ON u.id=m.user_id AND u.deleted=0 WHERE m.group_id=? AND m.deleted=0 AND m.id<? ORDER BY m.id DESC LIMIT 50",id,before==null?Long.MAX_VALUE:before);for(var row:rows){row.put("id",row.get("id").toString());row.put("mine",((Number)row.remove("user_id")).longValue()==uid);row.put("content",crypto.decrypt((String)row.get("content")));if(row.get("nickname")==null)row.put("nickname","已注销成员");}return rows;}
    @Transactional(noRollbackFor=BusinessException.class) public void send(long id,String content){long uid=access.consentedUser();var g=group(id,true);member(id,uid);if(((Number)g.get("status")).intValue()!=1)throw new BusinessException(3001,"小组已关闭，暂不能发言");if(safety.checkInput(uid,null,"group",content).getAction()==SafetyAction.BLOCK)throw new BusinessException(1001,"这条内容暂不适合发送。如需支持，请前往心理援助页面。");db.update("INSERT INTO group_message(id,group_id,user_id,content,create_time,update_time,deleted) VALUES(?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",IdWorker.getId(),id,uid,crypto.encrypt(content.trim()));}
    @Transactional public void remove(long id,long messageId){long uid=access.user();group(id,true);var m=member(id,uid);var messages=db.queryForList("SELECT user_id FROM group_message WHERE id=? AND group_id=? AND deleted=0",messageId,id);if(messages.isEmpty())throw new BusinessException(1004,"消息不存在");if(!"组长".equals(m.get("role"))&&((Number)messages.get(0).get("user_id")).longValue()!=uid)throw new BusinessException(1003,"只能删除本人消息");db.update("UPDATE group_message SET content='',deleted=1,update_time=CURRENT_TIMESTAMP WHERE id=?",messageId);}
}
