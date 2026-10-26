package com.zhiyu.modules;
import com.zhiyu.modules.growth.CourseLessonService;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class CourseLessonTest {
 @Autowired JdbcTemplate db;
 @Autowired CourseLessonService service;
 @BeforeEach void setup(){
  for(int i=0;i<3;i++)db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted) VALUES(?,?,'unused','测试同学',?,1,1,0)",980000L+i,"lesson"+i,i==0?1:0);
  for(int i=0;i<2;i++)db.update("INSERT INTO learning_program(id,owner_id,kind,title,description,location,co_leader,materials,start_time,end_time,capacity,status,create_time,update_time,deleted) VALUES(?,980000,'COURSE','课程','说明','校内','','',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,10,'PUBLISHED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",980010L+i);
  db.update("INSERT INTO program_booking(id,program_id,user_id,status,create_time,update_time,deleted) VALUES(980020,980010,980001,'BOOKED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)");
  actor(980000,1);
 }
 @AfterEach void cleanup(){UserContext.clear();}
 void actor(long id,int role){UserContext.set(new LoginUser(id,role));}
 CourseLessonService.Lesson lesson(int position,boolean published){return new CourseLessonService.Lesson("第一课","第一章",position,"材料与操作说明",null,published);}
 @Test void publishedLessonsAndOwnProgress(){
  long published=Long.parseLong(service.save(980010,null,lesson(1,true)));
  service.save(980010,null,lesson(2,false));
  assertEquals(2,service.list(980010).size());
  actor(980001,0);assertEquals(1,service.list(980010).size());
  service.progress(980010,published,true);service.progress(980010,published,true);
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM lesson_progress WHERE user_id=980001",Integer.class));
  assertEquals(1,((Number)service.list(980010).get(0).get("completed")).intValue());
  service.progress(980010,published,false);assertEquals(0,((Number)service.list(980010).get(0).get("completed")).intValue());
 }
 @Test void unbookedUserAndOtherHostDenied(){actor(980002,0);assertThrows(BusinessException.class,()->service.list(980010));assertThrows(BusinessException.class,()->service.save(980010,null,lesson(1,true)));}
 @Test void duplicateOrderRejected(){service.save(980010,null,lesson(1,true));assertThrows(BusinessException.class,()->service.save(980010,null,lesson(1,false)));}
 @Test void draftAndForeignLessonDenied(){long draft=Long.parseLong(service.save(980010,null,lesson(1,false)));long foreign=Long.parseLong(service.save(980011,null,lesson(1,true)));actor(980001,0);assertThrows(BusinessException.class,()->service.progress(980010,draft,true));assertThrows(BusinessException.class,()->service.progress(980010,foreign,true));}
 @Test void cancellationStopsProgress(){long lesson=Long.parseLong(service.save(980010,null,lesson(1,true)));db.update("UPDATE learning_program SET status='CANCELLED' WHERE id=980010");actor(980001,0);assertThrows(BusinessException.class,()->service.progress(980010,lesson,true));}
 @Test void deletionHidesLesson(){long lesson=Long.parseLong(service.save(980010,null,lesson(1,true)));service.delete(980010,lesson);assertEquals(0,service.list(980010).size());actor(980001,0);assertThrows(BusinessException.class,()->service.progress(980010,lesson,true));}
 @Test void unlistedProjectRejected(){assertThrows(BusinessException.class,()->service.save(980010,null,new CourseLessonService.Lesson("课节","章节",1,"内容",999999L,true)));}
}
