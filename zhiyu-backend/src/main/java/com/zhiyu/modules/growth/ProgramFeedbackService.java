package com.zhiyu.modules.growth;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.BusinessAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class ProgramFeedbackService {
    private final JdbcTemplate db;
    private final BusinessAccess access;
    private final AesEncryptor crypto;
    private final ObjectMapper json;
    public record Feedback(@NotNull @Min(1) @Max(5) Integer rating,@Size(max=1000) String note,@AssertTrue boolean consent){}
    private Map<String,Object> program(long id,boolean lock){var rows=db.queryForList("SELECT * FROM learning_program WHERE id=? AND deleted=0"+(lock?" FOR UPDATE":""),id);if(rows.isEmpty())throw new BusinessException(1004,"活动不存在");return rows.get(0);}
    private String reason(Map<String,Object> p,long uid){
        if(!"PUBLISHED".equals(p.get("status")))return "活动已取消或未开放，不能提交反馈";
        Object end=p.get("end_time");LocalDateTime endTime=end instanceof Timestamp t?t.toLocalDateTime():(LocalDateTime)end;
        if(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).isBefore(endTime))return "活动结束后再来记录体验";
        if(db.queryForObject("SELECT COUNT(*) FROM program_booking WHERE program_id=? AND user_id=? AND status='CHECKED_IN' AND deleted=0",Integer.class,p.get("id"),uid)==0)return "仅已签到的参加者可以填写，请向带领者确认签到";
        return "";
    }
    private Feedback decode(String cipher){try{return json.readValue(crypto.decrypt(cipher),Feedback.class);}catch(Exception e){throw new IllegalStateException("反馈读取失败",e);}}
    public Map<String,Object> own(long id){long uid=access.user();var p=program(id,false);if(!"PUBLISHED".equals(p.get("status"))&&((Number)p.get("owner_id")).longValue()!=uid&&db.queryForObject("SELECT COUNT(*) FROM program_booking WHERE program_id=? AND user_id=? AND deleted=0",Integer.class,id,uid)==0)throw new BusinessException(1004,"活动不存在");String reason=reason(p,uid);var records=db.queryForList("SELECT payload FROM wellbeing_record WHERE user_id=? AND kind='PROGRAM_FEEDBACK' AND record_key=? AND deleted=0",uid,Long.toString(id));Map<String,Object> result=new LinkedHashMap<>();result.put("title",p.get("title"));result.put("kind",p.get("kind"));result.put("eligible",reason.isEmpty());result.put("reason",reason);result.put("feedback",records.isEmpty()?null:decode((String)records.get(0).get("payload")));return result;}
    @Transactional public void save(long id,Feedback input){
        long uid=access.user();db.queryForObject("SELECT id FROM user WHERE id=? FOR UPDATE",Long.class,uid);access.consentedUser();
        if(!input.consent()||input.rating()==null||input.rating()<1||input.rating()>5||Objects.toString(input.note(),"").length()>1000)throw new BusinessException(1001,"请填写有效评分并确认保存授权");
        var p=program(id,true);String reason=reason(p,uid);if(!reason.isEmpty())throw new BusinessException(3001,reason);
        String cipher;try{cipher=crypto.encrypt(json.writeValueAsString(new Feedback(input.rating(),Objects.toString(input.note(),"").trim(),true)));}catch(Exception e){throw new IllegalStateException("反馈保存失败",e);}
        int updated=db.update("UPDATE wellbeing_record SET payload=?,deleted=0,update_time=CURRENT_TIMESTAMP WHERE user_id=? AND kind='PROGRAM_FEEDBACK' AND record_key=?",cipher,uid,Long.toString(id));
        if(updated==0)db.update("INSERT INTO wellbeing_record(id,user_id,kind,record_key,payload,create_time,update_time,deleted) VALUES(?,?,'PROGRAM_FEEDBACK',?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",IdWorker.getId(),uid,Long.toString(id),cipher);
    }
    @Transactional public void remove(long id){long uid=access.user();db.queryForObject("SELECT id FROM user WHERE id=? FOR UPDATE",Long.class,uid);db.update("DELETE FROM wellbeing_record WHERE user_id=? AND kind='PROGRAM_FEEDBACK' AND record_key=?",uid,Long.toString(id));}
    public Map<String,Object> summary(long id){
        long uid=access.user();access.role(1,2,3);var p=program(id,false);int role=db.queryForObject("SELECT role FROM user WHERE id=?",Integer.class,uid);if(role!=3&&((Number)p.get("owner_id")).longValue()!=uid&&("GUIDANCE".equals(p.get("kind"))||db.queryForObject("SELECT COUNT(*) FROM program_collaborator WHERE program_id=? AND user_id=? AND deleted=0",Integer.class,id,uid)==0))throw new BusinessException(1003,"只能查看本人带领的活动汇总");
        if("GUIDANCE".equals(p.get("kind")))return Map.of("visible",false,"count",0,"minimum",5,"reason","一对一反馈仅由参加者本人保存，不向带领者展示评分");
        if(!"PUBLISHED".equals(p.get("status")))return Map.of("visible",false,"count",0,"minimum",5,"reason","活动当前未开放，不展示汇总");
        var records=db.queryForList("SELECT r.payload FROM wellbeing_record r JOIN user u ON u.id=r.user_id AND u.deleted=0 AND u.status=1 AND u.privacy_agreed=1 JOIN program_booking b ON b.user_id=r.user_id AND b.program_id=? AND b.status='CHECKED_IN' AND b.deleted=0 WHERE r.kind='PROGRAM_FEEDBACK' AND r.record_key=? AND r.deleted=0",id,Long.toString(id));
        if(records.size()<5)return Map.of("visible",false,"count",records.size(),"minimum",5,"reason","至少五位参加者反馈后才展示评分，减少个体识别风险");
        int[] distribution=new int[5];int sum=0;for(var row:records){int rating=decode((String)row.get("payload")).rating();distribution[rating-1]++;sum+=rating;}
        return Map.of("visible",true,"count",records.size(),"minimum",5,"reason","只展示体验评分汇总，不提供个人笔记或身份","average",Math.round(sum*10.0/records.size())/10.0,"distribution",distribution);
    }
}
