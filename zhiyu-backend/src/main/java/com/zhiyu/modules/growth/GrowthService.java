package com.zhiyu.modules.growth;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GrowthService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final AesEncryptor crypto;
    private final ScaleCatalog scales;
    private final com.zhiyu.modules.notification.service.NotificationService notificationService;
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private LocalDateTime now(){return LocalDateTime.now(ZONE);}
    private Map<String,Object> one(String sql,Object...args){var rows=db.queryForList(sql,args);if(rows.isEmpty())throw new BusinessException(1004,"记录不存在");return rows.get(0);}
    private long number(Object o){return ((Number)o).longValue();}
    private long user(boolean consent){
        Long id=UserContext.getUserId();if(id==null)throw new BusinessException(1002,"请先登录");
        var u=one("SELECT id,status,privacy_agreed FROM user WHERE id=? AND deleted=0 FOR UPDATE",id);
        if(number(u.get("status"))!=1)throw new BusinessException(1003,"账号不可用");
        if(consent && number(u.get("privacy_agreed"))!=1)throw new BusinessException(1003,"请先阅读并同意隐私政策");
        return id;
    }
    private int role(long id){return db.queryForObject("SELECT role FROM user WHERE id=? AND deleted=0",Integer.class,id);}
    private void requireHost(long id){if(!List.of(1,2,3).contains(role(id)))throw new BusinessException(1003,"仅活动带领者可以操作");}
    private void own(Map<String,Object> p,long uid){requireHost(uid);if(number(p.get("owner_id"))!=uid && role(uid)!=3 && (p.get("id")==null || "GUIDANCE".equals(p.get("kind")) || !List.of(1,2).contains(role(uid)) || db.queryForObject("SELECT COUNT(*) FROM program_collaborator WHERE program_id=? AND user_id=? AND deleted=0",Integer.class,p.get("id"),uid)==0))throw new BusinessException(1003,"仅主办人可以操作");}
    private String encode(Object value){try{return crypto.encrypt(json.writeValueAsString(value));}catch(Exception e){throw new IllegalStateException("记录加密失败",e);}}
    private Map<String,Object> decode(String value){try{return json.readValue(crypto.decrypt(value),new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("记录读取失败",e);}}
    private void save(long uid,String kind,String key,Object payload){
        // Lock the owner row to serialize daily/draft/reflection upserts across concurrent requests.
        db.queryForObject("SELECT id FROM user WHERE id=? FOR UPDATE",Long.class,uid);
        String cipher=encode(payload);
        int count=db.update("UPDATE wellbeing_record SET payload=?,deleted=0,update_time=? WHERE user_id=? AND kind=? AND record_key=?",cipher,now(),uid,kind,key);
        if(count==0)db.update("INSERT INTO wellbeing_record(id,user_id,kind,record_key,payload,create_time,update_time,deleted) VALUES(?,?,?,?,?,?,?,0)",IdWorker.getId(),uid,kind,key,cipher,now(),now());
    }
    @Transactional public void mood(GrowthRequests.Mood req){if(!req.consent())throw new BusinessException(1001,"请确认保存授权");save(user(true),"MOOD",LocalDate.now(ZONE).toString(),req);}
    @Transactional public Map<String,Object> assessment(GrowthRequests.Assessment req){
        long uid=user(true);if(!req.consent())throw new BusinessException(1001,"请确认保存授权");
        var scale=scales.require(req.scale(),req.version());var result=scales.score(scale,req.answers(),req.draft());
        Map<String,Object> payload=new LinkedHashMap<>(result);payload.put("scale",req.scale());payload.put("version",req.version());payload.put("answers",req.answers());
        save(uid,req.draft()?"DRAFT":"ASSESSMENT",req.draft()?req.scale():UUID.randomUUID().toString(),payload);
        if(!req.draft())db.update("UPDATE wellbeing_record SET deleted=1,update_time=? WHERE user_id=? AND kind='DRAFT' AND record_key=?",now(),uid,req.scale());
        if(Boolean.TRUE.equals(result.get("crisis"))){
            // Fixed, de-identified wording; never include answers or original text in the care summary.
            Long existing=db.queryForObject("SELECT COUNT(*) FROM care_reminder WHERE user_id=? AND status=0 AND deleted=0 AND create_time>=?",Long.class,uid,LocalDate.now(ZONE).atStartOfDay());
            if(existing==0)db.update("INSERT INTO care_reminder(id,user_id,risk_level,summary,status,create_time,update_time,deleted) VALUES(?,?,2,?,0,?,?,0)",IdWorker.getId(),uid,"自评中出现需要及时关注的安全信号；请按授权流程跟进。",now(),now());
        }
        return result;
    }
    @Transactional public void reflection(GrowthRequests.Reflection req){
        long uid=user(true);if(!req.consent())throw new BusinessException(1001,"请确认保存授权");
        var s=one("SELECT status FROM craft_session WHERE id=? AND user_id=? AND deleted=0",req.sessionId(),uid);
        if(number(s.get("status"))!=3)throw new BusinessException(3001,"请完成创作后再保存感受");
        save(uid,"REFLECTION",req.sessionId().toString(),req);
    }
    public Map<String,Object> materialMap(){
        user(false);
        var rows=db.queryForList("SELECT p.id,p.title,p.category,p.est_minutes,s.medium,s.level,s.hazards,s.guidance,s.source FROM craft_project p LEFT JOIN craft_safety s ON s.project_id=p.id AND s.deleted=0 WHERE p.status=1 AND p.deleted=0 ORDER BY p.update_time DESC,p.id DESC LIMIT 201");
        boolean truncated=rows.size()>200;
        var items=rows.stream().limit(200).map(row->{
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("id",row.get("id").toString());item.put("title",row.get("title"));
            item.put("category",Objects.toString(row.get("category"),"未分类"));item.put("estMinutes",row.get("est_minutes"));
            for(String key:List.of("medium","hazards","guidance","source"))item.put(key,Objects.toString(row.get(key),""));
            item.put("level",Objects.toString(row.get("level"),"UNKNOWN"));return item;
        }).toList();
        return Map.of("items",items,"truncated",truncated);
    }

    public Map<String,Object> journey(){
        // S1（2026-11-04）：成长轨迹读取的是 MOOD/ASSESSMENT/REFLECTION 敏感记录，
        // 与 emotion 历史读取同标准，撤回同意后不再可读
        long uid=user(true);
        var rows=db.queryForList("SELECT id,kind,record_key,payload,create_time,update_time FROM wellbeing_record WHERE user_id=? AND deleted=0 ORDER BY update_time DESC,id DESC LIMIT 366",uid);
        boolean truncated=rows.size()>365;
        if(truncated)rows=rows.subList(0,365);
        List<Map<String,Object>> records=new ArrayList<>();
        for(var row:rows){var item=decode((String)row.get("payload"));item.put("id",row.get("id").toString());item.put("kind",row.get("kind"));item.put("key",row.get("record_key"));item.put("date",row.get("update_time").toString());records.add(item);}
        var summary=one("SELECT COUNT(*) AS completed,COALESCE(SUM(flow_seconds),0) AS flow_seconds,COUNT(DISTINCT CAST(end_time AS DATE)) AS days FROM craft_session WHERE user_id=? AND status=3 AND deleted=0",uid);
        int works=db.queryForObject("SELECT COUNT(*) FROM work WHERE user_id=? AND deleted=0",Integer.class,uid);
        List<String> badges=new ArrayList<>();if(number(summary.get("completed"))>0)badges.add("第一份创作");if(number(summary.get("completed"))>=5)badges.add("五次专注");
        Set<String> days=new HashSet<>();for(var row:records)if("MOOD".equals(row.get("kind")))days.add(row.get("key").toString());
        int streak=0;LocalDate day=LocalDate.now(ZONE);if(!days.contains(day.toString()))day=day.minusDays(1);while(days.contains(day.toString())){streak++;day=day.minusDays(1);}if(streak>=7)badges.add("连续七天记录");
        // S3（2026-11-04）：列表内嵌计数统一为 JSON number
        return Map.of("records",records,"completed",((Number)summary.get("completed")).intValue(),"flowSeconds",((Number)summary.get("flow_seconds")).intValue(),"days",((Number)summary.get("days")).intValue(),"works",works,"badges",badges,"streak",streak,"limit",365,"truncated",truncated);
    }
    @Transactional public void erase(){long uid=user(false);db.queryForObject("SELECT id FROM user WHERE id=? FOR UPDATE",Long.class,uid);db.update("DELETE FROM wellbeing_record WHERE user_id=?",uid);}
    private LocalDateTime date(Object value){if(value instanceof Timestamp t)return t.toLocalDateTime();return (LocalDateTime)value;}
    private Map<String,Object> publicProgram(Map<String,Object> row,long uid){
        Map<String,Object> v=new LinkedHashMap<>();for(String name:List.of("id","owner_id","kind","title","description","location","co_leader","materials","capacity","status"))v.put(name,row.get(name));
        v.put("id",row.get("id").toString());v.put("owner_id",row.get("owner_id").toString());v.put("start_time",date(row.get("start_time")).toString());v.put("end_time",date(row.get("end_time")).toString());
        v.put("booked",db.queryForObject("SELECT COUNT(*) FROM program_booking WHERE program_id=? AND status IN ('PENDING','BOOKED','CHECKED_IN') AND deleted=0",Integer.class,row.get("id")));
        var mine=db.queryForList("SELECT id,status FROM program_booking WHERE program_id=? AND user_id=? AND deleted=0",row.get("id"),uid);v.put("myStatus",mine.isEmpty()?"":mine.get(0).get("status"));v.put("myBookingId",mine.isEmpty()?"":mine.get(0).get("id").toString());v.put("canManage",role(uid)==3 || number(row.get("owner_id"))==uid || (!"GUIDANCE".equals(row.get("kind")) && List.of(1,2).contains(role(uid)) && db.queryForObject("SELECT COUNT(*) FROM program_collaborator WHERE program_id=? AND user_id=? AND deleted=0",Integer.class,row.get("id"),uid)>0));return v;
    }
    public List<Map<String,Object>> programs(boolean manage){
        long uid=user(false);List<Map<String,Object>> rows;
        if(manage){requireHost(uid);rows=role(uid)==3?db.queryForList("SELECT * FROM learning_program WHERE deleted=0 ORDER BY start_time DESC LIMIT 200"):db.queryForList("SELECT p.* FROM learning_program p WHERE p.deleted=0 AND (p.owner_id=? OR (p.kind<>'GUIDANCE' AND EXISTS(SELECT 1 FROM program_collaborator c WHERE c.program_id=p.id AND c.user_id=? AND c.deleted=0))) ORDER BY p.start_time DESC LIMIT 200",uid,uid);}
        else rows=db.queryForList("SELECT p.* FROM learning_program p WHERE p.deleted=0 AND (p.status='PUBLISHED' OR EXISTS(SELECT 1 FROM program_booking b WHERE b.program_id=p.id AND b.user_id=? AND b.deleted=0)) ORDER BY p.start_time DESC LIMIT 200",uid);
        return rows.stream().map(row->publicProgram(row,uid)).toList();
    }
    @Transactional public String program(Long id,GrowthRequests.Program req){
        long uid=user(true);requireHost(uid);
        if(!req.endTime().isAfter(req.startTime()) || req.endTime().isAfter(req.startTime().plusDays(90)))throw new BusinessException(1001,"结束时间须晚于开始时间，课程最长 90 天");
        if("GUIDANCE".equals(req.kind()) && req.capacity()!=1)throw new BusinessException(1001,"一对一时段名额必须为 1");
        long owner=uid;
        if(id!=null){var existing=one("SELECT * FROM learning_program WHERE id=? AND deleted=0",id);own(existing,uid);owner=number(existing.get("owner_id"));}
        db.queryForObject("SELECT id FROM user WHERE id=? FOR UPDATE",Long.class,owner);
        if("GUIDANCE".equals(req.kind()) && "PUBLISHED".equals(req.status())){
            long overlap=db.queryForObject("SELECT COUNT(*) FROM learning_program WHERE owner_id=? AND id<>? AND kind='GUIDANCE' AND status='PUBLISHED' AND deleted=0 AND start_time<? AND end_time>?",Long.class,owner,id==null?-1L:id,req.endTime(),req.startTime());
            if(overlap>0)throw new BusinessException(3001,"带领者在该时间已有一对一时段，请调整日程");
        }
        if(id==null){if(!req.startTime().isAfter(now()))throw new BusinessException(1001,"请设置未来的开始时间");id=IdWorker.getId();
            db.update("INSERT INTO learning_program(id,owner_id,kind,title,description,location,co_leader,materials,start_time,end_time,capacity,status,create_time,update_time,deleted) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",id,uid,req.kind(),req.title(),req.description(),req.location(),Objects.toString(req.coLeader(),""),Objects.toString(req.materials(),""),req.startTime(),req.endTime(),req.capacity(),req.status(),now(),now());
        }else{
            var p=one("SELECT * FROM learning_program WHERE id=? AND deleted=0 FOR UPDATE",id);own(p,uid);
            long booked=db.queryForObject("SELECT COUNT(*) FROM program_booking WHERE program_id=? AND status IN ('PENDING','BOOKED','CHECKED_IN') AND deleted=0",Long.class,id);
            if(booked>0 && (!req.startTime().equals(date(p.get("start_time"))) || !req.endTime().equals(date(p.get("end_time"))) || !req.kind().equals(p.get("kind")) || "DRAFT".equals(req.status())))throw new BusinessException(3001,"已有报名，不可改动日程、类型或撤回草稿；可取消活动");
            if(req.capacity()<booked)throw new BusinessException(3001,"容量不能小于已报名人数");
            db.update("UPDATE learning_program SET kind=?,title=?,description=?,location=?,co_leader=?,materials=?,start_time=?,end_time=?,capacity=?,status=?,update_time=? WHERE id=?",req.kind(),req.title(),req.description(),req.location(),Objects.toString(req.coLeader(),""),Objects.toString(req.materials(),""),req.startTime(),req.endTime(),req.capacity(),req.status(),now(),id);
        }return id.toString();
    }
    @Transactional public void book(long id,boolean cancel){
        long uid=user(!cancel);db.queryForObject("SELECT id FROM user WHERE id=? FOR UPDATE",Long.class,uid);var p=one("SELECT * FROM learning_program WHERE id=? AND deleted=0 FOR UPDATE",id);
        var rows=db.queryForList("SELECT * FROM program_booking WHERE program_id=? AND user_id=? AND deleted=0",id,uid);
        if(cancel){if(!now().isBefore(date(p.get("start_time"))) && !"CANCELLED".equals(p.get("status")))throw new BusinessException(3001,"活动已开始，请联系主办人");db.update("UPDATE program_booking SET status='CANCELLED',update_time=? WHERE program_id=? AND user_id=?",now(),id,uid);return;}
        if(!"PUBLISHED".equals(p.get("status")) || !now().isBefore(date(p.get("start_time"))))throw new BusinessException(3001,"报名已结束或活动未开放");
        if(number(p.get("owner_id"))==uid)throw new BusinessException(3001,"主办人无需报名自己的活动");
        if(!rows.isEmpty() && List.of("PENDING","BOOKED","CHECKED_IN").contains(rows.get(0).get("status")))return;
        long overlaps=db.queryForObject("SELECT COUNT(*) FROM program_booking b JOIN learning_program p ON p.id=b.program_id WHERE b.user_id=? AND b.status IN ('PENDING','BOOKED','CHECKED_IN') AND b.deleted=0 AND p.deleted=0 AND p.status='PUBLISHED' AND p.start_time<? AND p.end_time>?",Long.class,uid,date(p.get("end_time")),date(p.get("start_time")));
        if(overlaps>0)throw new BusinessException(3001,"该时间已有预约，请先取消冲突的安排");
        long count=db.queryForObject("SELECT COUNT(*) FROM program_booking WHERE program_id=? AND status IN ('PENDING','BOOKED','CHECKED_IN') AND deleted=0",Long.class,id);
        if(count>=number(p.get("capacity")))throw new BusinessException(3001,"名额已满，请选择其他时段");
        String bookingStatus="GUIDANCE".equals(p.get("kind"))?"PENDING":"BOOKED";
        if(rows.isEmpty())db.update("INSERT INTO program_booking(id,program_id,user_id,status,create_time,update_time,deleted) VALUES(?,?,?,?,?,?,0)",IdWorker.getId(),id,uid,bookingStatus,now(),now());
        else {db.update("UPDATE guidance_channel SET enabled=0,update_time=CURRENT_TIMESTAMP WHERE booking_id=?",rows.get(0).get("id"));db.update("UPDATE program_booking SET status=?,update_time=? WHERE id=?",bookingStatus,now(),rows.get(0).get("id"));}
    }
    @Transactional public void reviewBooking(long id,long booking,String decision){
        long uid=user(false);requireHost(uid);
        var p=one("SELECT * FROM learning_program WHERE id=? AND deleted=0 FOR UPDATE",id);own(p,uid);
        if(!List.of("CONFIRM","REJECT").contains(decision))throw new BusinessException(1001,"请选择确认或拒绝");
        if(!"GUIDANCE".equals(p.get("kind")) || !"PUBLISHED".equals(p.get("status")) || !now().isBefore(date(p.get("start_time"))))throw new BusinessException(3001,"仅可处理尚未开始的一对一预约");
        var bookingRow=one("SELECT id,user_id FROM program_booking WHERE id=? AND program_id=? AND status='PENDING' AND deleted=0 FOR UPDATE",booking,id);
        int changed=db.update("UPDATE program_booking SET status=?,update_time=? WHERE id=? AND program_id=? AND status='PENDING' AND deleted=0","CONFIRM".equals(decision)?"BOOKED":"REJECTED",now(),booking,id);
        if(changed!=1)throw new BusinessException(3001,"预约已处理或已取消，请刷新后查看");
        // 通知系统（2026-11-04）：预约回复触达预约用户；失败不阻断审核主流程
        try{notificationService.notifyBookingReply(number(bookingRow.get("user_id")),booking,Objects.toString(p.get("title"),"一对一预约"),"CONFIRM".equals(decision));}
        catch(Exception e){org.slf4j.LoggerFactory.getLogger(GrowthService.class).warn("预约回复通知写入失败（不阻断处理）: bookingId={}, err={}",booking,e.getMessage());}
    }
    public List<Map<String,Object>> attendees(long id){long uid=user(false);own(one("SELECT * FROM learning_program WHERE id=? AND deleted=0",id),uid);return db.queryForList("SELECT b.id,b.status,u.nickname FROM program_booking b JOIN user u ON u.id=b.user_id WHERE b.program_id=? AND b.deleted=0 ORDER BY b.create_time",id).stream().map(row->{row.put("id",row.get("id").toString());return row;}).toList();}
    /**
     * AU2（2026-11-04）：管理端按预约 ID 反查——预约详情 + 所属节目摘要。
     * 起点只有 bookingId（如 BOOKING_REPLY 通知 refId、运营排查），program 未知，
     * 故不能用 program 作用域的既有 URL；仅管理员（SecurityConfig /api/admin/** 兜底 + 此处显式复核）。
     * 用户标识只给 userId（雪花字符串）供关联定位，昵称属多余暴露不下发（最小权限）。
     */
    public Map<String,Object> adminBookingDetail(long bookingId){
        long uid=user(false);
        if(role(uid)!=3)throw new BusinessException(1003,"仅管理员可按预约 ID 反查");
        var rows=db.queryForList("SELECT b.id,b.program_id,b.user_id,b.status,b.create_time,b.update_time,"
                + "p.title,p.kind,p.start_time,p.end_time,p.status AS program_status,p.owner_id"
                + " FROM program_booking b LEFT JOIN learning_program p ON p.id=b.program_id AND p.deleted=0"
                + " WHERE b.id=? AND b.deleted=0",bookingId);
        if(rows.isEmpty())throw new BusinessException(1004,"预约不存在");
        var b=rows.get(0);
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("id",String.valueOf(b.get("id")));
        out.put("programId",String.valueOf(b.get("program_id")));
        out.put("userId",String.valueOf(b.get("user_id")));
        out.put("status",b.get("status"));
        out.put("createTime",String.valueOf(b.get("create_time")));
        out.put("updateTime",String.valueOf(b.get("update_time")));
        Map<String,Object> program=null;
        if(b.get("title")!=null){
            program=new LinkedHashMap<>();
            program.put("id",String.valueOf(b.get("program_id")));
            program.put("title",b.get("title"));
            program.put("kind",b.get("kind"));
            program.put("startTime",String.valueOf(b.get("start_time")));
            program.put("endTime",String.valueOf(b.get("end_time")));
            program.put("status",b.get("program_status"));
            program.put("ownerId",String.valueOf(b.get("owner_id")));
        }
        out.put("program",program);
        return out;
    }
    @Transactional public void checkin(long id,long booking){long uid=user(false);var p=one("SELECT * FROM learning_program WHERE id=? AND deleted=0 FOR UPDATE",id);own(p,uid);
        if(!"PUBLISHED".equals(p.get("status")) || now().isBefore(date(p.get("start_time")).minusMinutes(30)) || now().isAfter(date(p.get("end_time")).plusHours(24)))throw new BusinessException(3001,"当前不在签到时间内");
        int changed=db.update("UPDATE program_booking SET status='CHECKED_IN',update_time=? WHERE id=? AND program_id=? AND status='BOOKED' AND deleted=0",now(),booking,id);if(changed==0)throw new BusinessException(3001,"报名记录不可签到或已经签到");}
    public Map<String,Object> safety(long id){long uid=user(false);var p=one("SELECT inheritor_id,status FROM craft_project WHERE id=? AND deleted=0",id);if(number(p.get("status"))!=1 && number(p.get("inheritor_id"))!=uid && role(uid)!=3)throw new BusinessException(1004,"项目未公开");var list=db.queryForList("SELECT medium,level,hazards,guidance,source FROM craft_safety WHERE project_id=? AND deleted=0",id);return list.isEmpty()?Map.of("level","UNKNOWN","guidance","本项目尚未完成材料安全标注，请先向带领者确认材料与工具。","medium","","hazards","","source",""):list.get(0);}
    @Transactional public void safety(long id,GrowthRequests.Safety req){long uid=user(false);var p=one("SELECT inheritor_id FROM craft_project WHERE id=? AND deleted=0 FOR UPDATE",id);if(number(p.get("inheritor_id"))!=uid && role(uid)!=3)throw new BusinessException(1003,"只能维护本人项目");int updated=db.update("UPDATE craft_safety SET medium=?,level=?,hazards=?,guidance=?,source=?,update_time=?,deleted=0 WHERE project_id=?",req.medium(),req.level(),req.hazards(),req.guidance(),req.source(),now(),id);if(updated==0)db.update("INSERT INTO craft_safety(id,project_id,medium,level,hazards,guidance,source,create_time,update_time,deleted) VALUES(?,?,?,?,?,?,?,?,?,0)",IdWorker.getId(),id,req.medium(),req.level(),req.hazards(),req.guidance(),req.source(),now(),now());}
}
