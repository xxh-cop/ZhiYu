package com.zhiyu.modules.care;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.BusinessAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
@RequiredArgsConstructor
public class CareWorkflowService {
    private final JdbcTemplate jdbc;
    private final BusinessAccess access;
    private final OperationLog audit;
    public Map<String,Object> detail(Long id){
        Long actor=access.role(2,3);
        int actorRole=jdbc.queryForObject("SELECT role FROM user WHERE id=?",Integer.class,actor);
        var rows=jdbc.queryForList("SELECT r.id,r.summary,r.risk_level,r.status,r.create_time,COALESCE(u.can_contact,0) can_contact FROM care_reminder r LEFT JOIN user u ON u.id=r.user_id AND u.deleted=0 AND u.status=1 WHERE r.id=? AND r.deleted=0",id);
        if(rows.isEmpty())throw new BusinessException(1004,"关怀提醒不存在");
        var r=rows.get(0);r.put("id",id.toString());
        var cases=jdbc.queryForList("SELECT owner_id,state FROM care_case WHERE reminder_id=? AND deleted=0",id);
        Object owner=cases.isEmpty()?null:cases.get(0).get("owner_id");
        // 授权：管理员（role=3）可看全部；咨询师（role=2）只能看自己领取的 case，
        // 未领取 case 不对咨询师开放（避免 summary/can_contact 在咨询师之间横向泄露）。
        if(actorRole!=3){
            if(owner==null)throw new BusinessException(1003,"尚未领取的关怀任务仅管理员可查看");
            if(!owner.toString().equals(actor.toString()))throw new BusinessException(1003,"仅负责人或管理员可查看关怀详情");
        }
        r.put("state",cases.isEmpty()?"OPEN":cases.get(0).get("state"));
        r.put("claimed",owner!=null);r.put("mine",owner!=null&&owner.toString().equals(actor.toString()));
        r.put("events",jdbc.queryForList("SELECT action,create_time FROM care_followup WHERE reminder_id=? AND deleted=0 ORDER BY create_time,id",id));
        return r;
    }
    @Transactional
    public void follow(Long id,String action){
        Long actor=access.role(2,3);
        if(action==null||!List.of("CLAIM","REVIEWED","CONTACTED","REFERRED","CLOSE","REOPEN").contains(action)) throw new BusinessException(1001,"不支持的跟进操作");
        var reminders=jdbc.queryForList("SELECT user_id FROM care_reminder WHERE id=? AND deleted=0 FOR UPDATE",id);
        if(reminders.isEmpty())throw new BusinessException(1004,"关怀提醒不存在");
        // 只认未删除的 case；软删除的历史行不再提供 owner/state。
        var cases=jdbc.queryForList("SELECT owner_id,state FROM care_case WHERE reminder_id=? AND deleted=0",id);
        if(cases.isEmpty()){
            // care_case.reminder_id 有 UNIQUE 约束：若存在软删除行，先复用并重置，避免插入冲突。
            int restored=jdbc.update("UPDATE care_case SET deleted=0,owner_id=NULL,state='OPEN',update_time=CURRENT_TIMESTAMP WHERE reminder_id=? AND deleted=1",id);
            if(restored==0)jdbc.update("INSERT INTO care_case(id,reminder_id,state,create_time,update_time,deleted) VALUES(?,?,'OPEN',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",IdWorker.getId(),id);
            cases=jdbc.queryForList("SELECT owner_id,state FROM care_case WHERE reminder_id=? AND deleted=0",id);
        }
        Object owner=cases.isEmpty()?null:cases.get(0).get("owner_id");
        String state=cases.isEmpty()?"OPEN":(String)cases.get(0).get("state");
        boolean admin=jdbc.queryForObject("SELECT role FROM user WHERE id=?",Integer.class,actor)==3;
        if(action.equals("CLAIM")){
            if(owner!=null||state.equals("CLOSED"))throw new BusinessException(3001,"任务已领取或已完成");
            jdbc.update("UPDATE care_case SET owner_id=?,update_time=CURRENT_TIMESTAMP WHERE reminder_id=? AND deleted=0",actor,id);
        } else {
            if(owner==null)throw new BusinessException(3001,"请先领取关怀任务");
            if(!admin&&!owner.toString().equals(actor.toString()))throw new BusinessException(1003,"仅负责人或管理员可跟进");
            if(action.equals("REOPEN")?!state.equals("CLOSED"):state.equals("CLOSED"))throw new BusinessException(3001,"任务状态已变化，请刷新");
            if(action.equals("CONTACTED")){
                var users=jdbc.queryForList("SELECT can_contact FROM user WHERE id=? AND deleted=0 AND status=1 FOR UPDATE",reminders.get(0).get("user_id"));
                if(users.isEmpty()||!Integer.valueOf(1).equals(((Number)users.get(0).get("can_contact")).intValue()))throw new BusinessException(1003,"用户未授权联系或已撤回授权");
            }
            String next=action.equals("CLOSE")?"CLOSED":action.equals("REOPEN")?"OPEN":state;
            jdbc.update("UPDATE care_case SET state=?,update_time=CURRENT_TIMESTAMP WHERE reminder_id=? AND deleted=0",next,id);
            jdbc.update("UPDATE care_reminder SET status=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND deleted=0",action.equals("REOPEN")?0:1,id);
        }
        jdbc.update("INSERT INTO care_followup(id,reminder_id,actor_id,action,create_time,update_time,deleted) VALUES(?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",IdWorker.getId(),id,actor,action);
        audit.record("care."+action,id);
    }
}
