package com.zhiyu.modules;
import com.zhiyu.modules.growth.ProgramFeedbackService;
import com.zhiyu.modules.growth.ProgramFeedbackService.Feedback;
import com.zhiyu.security.*;
import com.zhiyu.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test") @Transactional
class ProgramFeedbackTest {
 @Autowired JdbcTemplate db;@Autowired ProgramFeedbackService service;
 @BeforeEach void seed(){for(int i=0;i<7;i++)db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted) VALUES(?,?,'unused','反馈测试用户',?,1,1,0)",996000+i,"feedbackqa"+i,i<2?1:0);var end=LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusHours(1);db.update("INSERT INTO learning_program(id,owner_id,kind,title,description,location,co_leader,materials,start_time,end_time,capacity,status,create_time,update_time,deleted) VALUES(996010,996000,'WORKSHOP','测试活动','内容','教室','','',?,?,20,'PUBLISHED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",end.minusHours(1),end);for(int i=2;i<7;i++)db.update("INSERT INTO program_booking(id,program_id,user_id,status,create_time,update_time,deleted) VALUES(?,996010,?,'CHECKED_IN',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",996020+i,996000+i);actor(996002);}
 void actor(long id){UserContext.set(new LoginUser(id,id<996002?1:0));}
 @AfterEach void clear(){UserContext.clear();}
 @Test void encryptedUpsertAndDelete(){service.save(996010,new Feedback(4,"私密活动笔记",true));assertFalse(db.queryForObject("SELECT payload FROM wellbeing_record WHERE user_id=996002",String.class).contains("私密"));service.save(996010,new Feedback(5,"更新笔记",true));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM wellbeing_record WHERE user_id=996002",Integer.class));assertEquals(5,((Feedback)service.own(996010).get("feedback")).rating());actor(996003);assertNull(service.own(996010).get("feedback"));service.remove(996010);actor(996002);assertNotNull(service.own(996010).get("feedback"));service.remove(996010);assertNull(service.own(996010).get("feedback"));}
 @Test void eligibilityAndConsentEnforced(){db.update("UPDATE program_booking SET status='BOOKED' WHERE user_id=996002");assertThrows(BusinessException.class,()->service.save(996010,new Feedback(4,"",true)));db.update("UPDATE program_booking SET status='CHECKED_IN' WHERE user_id=996002");db.update("UPDATE user SET privacy_agreed=0 WHERE id=996002");assertThrows(BusinessException.class,()->service.save(996010,new Feedback(4,"",true)));}
 @Test void futureAndCancelledProgramsReject(){db.update("UPDATE learning_program SET end_time=? WHERE id=996010",LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(1));assertEquals(false,service.own(996010).get("eligible"));assertThrows(BusinessException.class,()->service.save(996010,new Feedback(3,"",true)));db.update("UPDATE learning_program SET status='CANCELLED' WHERE id=996010");assertThrows(BusinessException.class,()->service.save(996010,new Feedback(3,"",true)));}
 @Test void thresholdAndHostScopeProtectSummary(){service.save(996010,new Feedback(1,"不能公开的笔记",true));actor(996000);var hidden=service.summary(996010);assertEquals(false,hidden.get("visible"));assertFalse(hidden.containsKey("average"));actor(996001);assertThrows(BusinessException.class,()->service.summary(996010));for(long id=996003;id<=996006;id++){actor(id);service.save(996010,new Feedback(5,"仅本人可见的测试原文QA",true));}actor(996000);var aggregate=service.summary(996010);assertEquals(true,aggregate.get("visible"));assertEquals(4.2,aggregate.get("average"));assertFalse(aggregate.toString().contains("仅本人可见的测试原文QA"));actor(996002);service.remove(996010);actor(996000);assertEquals(false,service.summary(996010).get("visible"));}
 @Test void guidanceNeverSharesRatings(){db.update("UPDATE learning_program SET kind='GUIDANCE' WHERE id=996010");service.save(996010,new Feedback(1,"一对一记录",true));actor(996000);var result=service.summary(996010);assertEquals(false,result.get("visible"));assertFalse(result.containsKey("average"));}
}
