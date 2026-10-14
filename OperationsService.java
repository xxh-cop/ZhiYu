package com.zhiyu.modules.admin.service;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OperationsService {
 private final JdbcTemplate db;
 private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
 @Transactional(readOnly=true)
 public Map<String,Object> overview(int days){
  Long uid=UserContext.getUserId();if(uid==null)throw new BusinessException(1002,"请先登录");
  if(db.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role=3 AND status=1 AND deleted=0",Integer.class,uid)!=1)throw new BusinessException(1003,"仅管理员可查看运营汇总");
  if(days!=7&&days!=30)throw new BusinessException(1001,"请选择7天或30天");
  var today=LocalDate.now(ZONE);var start=today.minusDays(days-1).atStartOfDay();var end=today.plusDays(1).atStartOfDay();
  var users=daily("SELECT DATE(create_time) AS bucket_date,COUNT(*) amount FROM user WHERE deleted=0 AND create_time>=? AND create_time<? GROUP BY DATE(create_time)",start,end);
  var works=daily("SELECT DATE(create_time) AS bucket_date,COUNT(*) amount FROM work WHERE deleted=0 AND create_time>=? AND create_time<? GROUP BY DATE(create_time)",start,end);
  var sessions=daily("SELECT DATE(start_time) AS bucket_date,COUNT(*) amount FROM craft_session WHERE deleted=0 AND status IN(1,2,3,4) AND start_time>=? AND start_time<? GROUP BY DATE(start_time)",start,end);
  var finished=daily("SELECT DATE(end_time) AS bucket_date,COUNT(*) amount FROM craft_session WHERE deleted=0 AND status=3 AND end_time>=? AND end_time<? GROUP BY DATE(end_time)",start,end);
  var timeline=new ArrayList<Map<String,Object>>();for(int i=0;i<days;i++){String date=today.minusDays(days-1-i).toString();timeline.add(Map.of("date",date,"users",users.getOrDefault(date,0L),"works",works.getOrDefault(date,0L),"sessions",sessions.getOrDefault(date,0L),"finished",finished.getOrDefault(date,0L)));}
  var totals=Map.of("users",count("SELECT COUNT(*) FROM user WHERE deleted=0"),"works",count("SELECT COUNT(*) FROM work WHERE deleted=0"),"projects",count("SELECT COUNT(*) FROM craft_project WHERE deleted=0 AND status=1"),"pending",count("SELECT COUNT(*) FROM craft_project WHERE deleted=0 AND status=0"),"care",count("SELECT COUNT(*) FROM care_reminder WHERE deleted=0 AND status=0"),"sessions",count("SELECT COUNT(*) FROM craft_session WHERE deleted=0 AND status IN(1,2,3,4)"));
  var result=new LinkedHashMap<String,Object>();result.put("totals",totals);result.put("daily",timeline);result.put("days",days);result.put("generatedAt",ZonedDateTime.now(ZONE).toOffsetDateTime().toString());
  result.put("categories",distribution("SELECT COALESCE(NULLIF(category,''),'未分类') label,COUNT(*) amount FROM craft_project WHERE deleted=0 AND status=1 GROUP BY category ORDER BY amount DESC,label LIMIT 12",Map.of()));
  result.put("projectStates",distribution("SELECT CAST(status AS CHAR) label,COUNT(*) amount FROM craft_project WHERE deleted=0 GROUP BY status",Map.of("0","待审核","1","已上架","2","已下架")));
  result.put("sessionStates",distribution("SELECT CAST(status AS CHAR) label,COUNT(*) amount FROM craft_session WHERE deleted=0 GROUP BY status",Map.of("0","待开始","1","进行中","2","已暂停","3","已完成","4","已放弃")));
  result.put("programKinds",distribution("SELECT kind label,COUNT(*) amount FROM learning_program WHERE deleted=0 AND status='PUBLISHED' GROUP BY kind",Map.of("WORKSHOP","工作坊","COURSE","系列课程","GUIDANCE","个别指导")));
  result.put("bookingStates",distribution("SELECT status label,COUNT(*) amount FROM program_booking WHERE deleted=0 GROUP BY status",Map.of("BOOKED","已预约","CHECKED_IN","已签到","CANCELLED","已取消")));
  return result;
 }
 private long count(String sql){return db.queryForObject(sql,Long.class);}
 private Map<String,Long> daily(String sql,LocalDateTime start,LocalDateTime end){var values=new HashMap<String,Long>();db.query(sql,rs->{values.put(rs.getString("bucket_date"),rs.getLong("amount"));},start,end);return values;}
 private List<Map<String,Object>> distribution(String sql,Map<String,String> labels){return db.query(sql,(rs,n)->{String label=rs.getString("label");return Map.<String,Object>of("name",labels.getOrDefault(label,label==null?"未标注":label),"value",rs.getLong("amount"));});}
}
