package com.zhiyu.modules.growth;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.ai.safety.SafetyAction;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.BusinessAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Separate, revocable student opt-in. Being an administrator never grants access. */
@Service @RequiredArgsConstructor
public class GuidanceConversationService {
    private final JdbcTemplate db;
    private final BusinessAccess access;
    private final AesEncryptor crypto;
    private final ContentSafetyFilter safety;
    private Map<String,Object> booking(long id,long uid,boolean lock){var rows=db.queryForList("SELECT b.id,b.user_id,b.status,p.owner_id,p.status program_status FROM program_booking b JOIN learning_program p ON p.id=b.program_id WHERE b.id=? AND b.deleted=0 AND p.deleted=0 AND p.kind='GUIDANCE'"+(lock?" FOR UPDATE":""),id);if(rows.isEmpty())throw new BusinessException(1004,"预约不存在");var b=rows.get(0);if(((Number)b.get("user_id")).longValue()!=uid&&((Number)b.get("owner_id")).longValue()!=uid)throw new BusinessException(1003,"仅预约双方可以访问");return b;}
    private boolean writable(Map<String,Object> b){return List.of("BOOKED","CHECKED_IN").contains(b.get("status"))&&"PUBLISHED".equals(b.get("program_status"))&&db.queryForObject("SELECT COUNT(*) FROM user WHERE id IN (?,?) AND status=1 AND deleted=0 AND privacy_agreed=1",Integer.class,b.get("user_id"),b.get("owner_id"))==2&&db.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role IN (1,2,3) AND deleted=0 AND status=1",Integer.class,b.get("owner_id"))==1;}
    public List<Map<String,Object>> conversations(){long uid=access.user();var rows=db.queryForList("SELECT b.id,p.title,b.status,p.status program_status,b.user_id,p.owner_id,COALESCE(c.enabled,0) enabled FROM program_booking b JOIN learning_program p ON p.id=b.program_id LEFT JOIN guidance_channel c ON c.booking_id=b.id AND c.deleted=0 WHERE b.deleted=0 AND p.deleted=0 AND p.kind='GUIDANCE' AND (b.user_id=? OR p.owner_id=?) ORDER BY b.update_time DESC,b.id DESC LIMIT 200",uid,uid);for(var r:rows){r.put("id",r.get("id").toString());r.put("isStudent",((Number)r.get("user_id")).longValue()==uid);r.put("canSend",((Number)r.get("enabled")).intValue()==1&&writable(r));r.remove("user_id");r.remove("owner_id");}return rows;}
    @Transactional public void consent(long id,boolean enabled){long uid=enabled?access.consentedUser():access.user();var b=booking(id,uid,true);if(((Number)b.get("user_id")).longValue()!=uid)throw new BusinessException(1003,"仅预约学生可以开启交流");if(enabled&&!writable(b))throw new BusinessException(3001,"预约未确认或当前不可交流");if(db.update("UPDATE guidance_channel SET enabled=?,deleted=0,update_time=CURRENT_TIMESTAMP WHERE booking_id=?",enabled?1:0,id)==0)db.update("INSERT INTO guidance_channel(id,booking_id,enabled) VALUES(?,?,?)",IdWorker.getId(),id,enabled?1:0);}
    public List<Map<String,Object>> messages(long id,Long before){long uid=access.user();booking(id,uid,false);var rows=db.queryForList("SELECT id,user_id,content,create_time FROM guidance_message WHERE booking_id=? AND deleted=0 AND id<? ORDER BY id DESC LIMIT 50",id,before==null?Long.MAX_VALUE:before);for(var r:rows){r.put("id",r.get("id").toString());r.put("mine",((Number)r.remove("user_id")).longValue()==uid);r.put("content",crypto.decrypt((String)r.get("content")));}return rows;}
    @Transactional(noRollbackFor=BusinessException.class) public void send(long id,String content){long uid=access.consentedUser();var b=booking(id,uid,true);if(!writable(b)||db.queryForObject("SELECT COUNT(*) FROM guidance_channel WHERE booking_id=? AND enabled=1 AND deleted=0",Integer.class,id)!=1)throw new BusinessException(3001,"交流未开启或预约已结束，请刷新状态");if(content==null||content.isBlank()||content.length()>1000)throw new BusinessException(1001,"请输入 1 至 1000 字");if(safety.checkInput(uid,null,"guidance",content).getAction()==SafetyAction.BLOCK)throw new BusinessException(1001,"暂不能发送此内容。如需及时支持，请使用心理援助入口。");db.update("INSERT INTO guidance_message(id,booking_id,user_id,content) VALUES(?,?,?,?)",IdWorker.getId(),id,uid,crypto.encrypt(content.trim()));}
    @Transactional public void remove(long id,long message){long uid=access.user();booking(id,uid,true);if(db.update("UPDATE guidance_message SET content='',deleted=1,update_time=CURRENT_TIMESTAMP WHERE id=? AND booking_id=? AND user_id=? AND deleted=0",message,id,uid)!=1)throw new BusinessException(1004,"消息不存在或不是本人发送");}
}
