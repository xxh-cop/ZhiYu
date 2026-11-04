package com.zhiyu.modules.growth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class GrowthServiceTest {
    JdbcTemplate db; GrowthService service; TransactionTemplate tx;
    final ScaleCatalog scales=new ScaleCatalog();
    @BeforeEach void setup() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:growth"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER","sa","");
        try(var connection=source.getConnection()){ScriptUtils.executeSqlScript(connection,new FileSystemResource("src/test/resources/schema-h2.sql"));}
        db=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        service=new GrowthService(db,new ObjectMapper().findAndRegisterModules(),new AesEncryptor(Base64.getEncoder().encodeToString(new byte[32])),scales,
                org.mockito.Mockito.mock(com.zhiyu.modules.notification.service.NotificationService.class));
        for(long id=1;id<=4;id++) db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted) VALUES(?,?,?,?,?,1,1,0)",id,"u"+id,"hash","测试"+id,id==1?1:0);
        login(2);
    }
    @AfterEach void clean(){UserContext.clear();}
    void login(long id){UserContext.set(new LoginUser(id,id==1?1:0));}
    GrowthRequests.Program program(int capacity){var start=LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(1).withNano(0);return new GrowthRequests.Program("WORKSHOP","测试工作坊","说明","教室","","",start,start.plusHours(1),capacity,"PUBLISHED");}
    long create(int capacity){login(1);long id=Long.parseLong(tx.execute(s->service.program(null,program(capacity))));login(2);return id;}
    @Test void materialMapOnlyExposesPublishedNondeletedProjects(){
        db.update("INSERT INTO craft_project(id,inheritor_id,title,status,deleted) VALUES(91,1,'public',1,0),(92,1,'private',0,0),(93,1,'deleted',1,1)");
        var items=(java.util.List<?>)service.materialMap().get("items");assertEquals(1,items.size());
        var item=(java.util.Map<?,?>)items.get(0);assertEquals("91",item.get("id"));assertEquals("UNKNOWN",item.get("level"));
    }
    @Test void recordsAreEncryptedOwnedAndDailyIdempotent(){
        tx.executeWithoutResult(s->service.mood(new GrowthRequests.Mood(4,"我的私密感受",true)));
        tx.executeWithoutResult(s->service.mood(new GrowthRequests.Mood(3,"更新后的感受",true)));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM wellbeing_record",Integer.class));
        assertFalse(db.queryForObject("SELECT payload FROM wellbeing_record",String.class).contains("感受"));
        assertEquals(1,((List<?>)service.journey().get("records")).size());login(3);
        assertTrue(((List<?>)service.journey().get("records")).isEmpty());service.erase();
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM wellbeing_record",Integer.class));
    }
    @Test void consentAndRoleAreCheckedAgainstDatabase(){
        db.update("UPDATE user SET privacy_agreed=0 WHERE id=2");
        assertThrows(BusinessException.class,()->service.mood(new GrowthRequests.Mood(3,"",true)));
        assertThrows(BusinessException.class,()->service.program(null,program(1)));
    }
    @Test void fixedScalesRejectInvalidAnswersAndRaiseCareForQuestionNine(){
        var scale=scales.available().get(0);
        assertThrows(BusinessException.class,()->scales.score(scale,List.of(1,2),false));
        var answers=new ArrayList<>(Collections.nCopies(9,0));answers.set(8,1);
        var req=new GrowthRequests.Assessment(scale.id(),scale.version(),answers,true,false);
        var result=tx.execute(s->service.assessment(req));assertEquals(1,result.get("score"));assertEquals(true,result.get("crisis"));
        tx.executeWithoutResult(s->service.assessment(req));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM care_reminder",Integer.class));
        assertFalse(db.queryForObject("SELECT summary FROM care_reminder",String.class).contains("answers"));
    }
    @Test void reflectionRequiresOwnedCompletedSession(){
        var req=new GrowthRequests.Reflection(99L,"我的作品",2,4,"平静","",true);
        assertThrows(BusinessException.class,()->service.reflection(req));
    }

    @Test void reflectionSavesAiSummaryInEncryptedPayload(){
        db.update("INSERT INTO craft_project(id,inheritor_id,title,status,deleted) VALUES(701,1,'折扇小景',1,0)");
        db.update("INSERT INTO craft_session(id,user_id,project_id,status,current_step,deleted) VALUES(702,2,701,3,4,0)");
        var crypto=new AesEncryptor(Base64.getEncoder().encodeToString(new byte[32]));
        var req=new GrowthRequests.Reflection(702L,"期末周的一片叶子",2,4,"平静","数着折线慢慢安静下来","你完成了四个步骤。",true);
        tx.executeWithoutResult(s->service.reflection(req));
        String payload=db.queryForObject("SELECT payload FROM wellbeing_record WHERE user_id=2 AND kind='REFLECTION'",String.class);
        assertTrue(crypto.decrypt(payload).contains("你完成了四个步骤。"));
        assertFalse(payload.contains("期末周的一片叶子"));
    }
    @Test void journeyExplicitlyReportsTruncationAndStableOrdering(){
        var crypto=new AesEncryptor(Base64.getEncoder().encodeToString(new byte[32]));
        for(int i=0;i<366;i++)db.update("INSERT INTO wellbeing_record(id,user_id,kind,record_key,payload,create_time,update_time,deleted) VALUES(?,2,'MOOD',?,?,CURRENT_TIMESTAMP,'2026-10-01 12:00:00',0)",990000+i,"key"+i,crypto.encrypt("{\"mood\":3}"));
        var result=service.journey();var records=(List<Map<String,Object>>)result.get("records");
        assertEquals(true,result.get("truncated"));assertEquals(365,records.size());assertEquals("990365",records.get(0).get("id"));assertEquals("990001",records.get(364).get("id"));
        login(3);assertEquals(false,service.journey().get("truncated"));assertTrue(((List<?>)service.journey().get("records")).isEmpty());
    }
    @Test void bookingIsIdempotentCancellationReleasesSeatAndOverlapIsRejected(){
        long id=create(1),other=create(1);login(2);
        tx.executeWithoutResult(s->service.book(id,false));tx.executeWithoutResult(s->service.book(id,false));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM program_booking",Integer.class));
        assertThrows(BusinessException.class,()->tx.executeWithoutResult(s->service.book(other,false)));
        login(3);assertThrows(BusinessException.class,()->tx.executeWithoutResult(s->service.book(id,false)));
        login(2);tx.executeWithoutResult(s->service.book(id,true));login(3);tx.executeWithoutResult(s->service.book(id,false));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM program_booking WHERE status='BOOKED'",Integer.class));
    }
    @Test void hostCannotPublishOverlappingGuidanceSlots(){
        login(1);var p=program(1);var req=new GrowthRequests.Program("GUIDANCE",p.title(),p.description(),p.location(),"","",p.startTime(),p.endTime(),1,"PUBLISHED");
        tx.executeWithoutResult(s->service.program(null,req));
        assertThrows(BusinessException.class,()->tx.executeWithoutResult(s->service.program(null,req)));
    }
    @Test void lastSeatCannotBeOversold() throws Exception {
        long id=create(1);var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try{List<Future<Boolean>> futures=new ArrayList<>();for(long uid:new long[]{2,3})futures.add(pool.submit(()->{login(uid);gate.await();try{tx.executeWithoutResult(s->service.book(id,false));return true;}catch(BusinessException e){return false;}finally{UserContext.clear();}}));
            gate.countDown();int successes=0;for(var f:futures)if(f.get(10,TimeUnit.SECONDS))successes++;assertEquals(1,successes);
        }finally{pool.shutdownNow();}
    }
    @Test void guidanceRequiresConfirmationAndRejectReleasesSeat(){
        login(1);var p=program(1);var req=new GrowthRequests.Program("GUIDANCE",p.title(),p.description(),p.location(),"","",p.startTime(),p.endTime(),1,"PUBLISHED");
        long id=Long.parseLong(tx.execute(s->service.program(null,req)));
        login(2);tx.executeWithoutResult(s->service.book(id,false));
        long booking=db.queryForObject("SELECT id FROM program_booking WHERE program_id=?",Long.class,id);
        assertEquals("PENDING",service.programs(false).get(0).get("myStatus"));
        assertThrows(BusinessException.class,()->tx.executeWithoutResult(s->service.reviewBooking(id,booking,"CONFIRM")));
        login(3);assertThrows(BusinessException.class,()->tx.executeWithoutResult(s->service.book(id,false)));
        login(1);tx.executeWithoutResult(s->service.reviewBooking(id,booking,"REJECT"));
        assertEquals(0,service.programs(true).get(0).get("booked"));
        assertThrows(BusinessException.class,()->tx.executeWithoutResult(s->service.reviewBooking(id,booking,"CONFIRM")));
        login(3);tx.executeWithoutResult(s->service.book(id,false));
        long next=db.queryForObject("SELECT id FROM program_booking WHERE program_id=? AND user_id=3",Long.class,id);
        login(1);tx.executeWithoutResult(s->service.reviewBooking(id,next,"CONFIRM"));
        login(3);assertEquals("BOOKED",service.programs(false).get(0).get("myStatus"));
    }
    @Test void cancelledPendingBookingCannotBeConfirmed(){
        long id=create(1);db.update("UPDATE learning_program SET kind='GUIDANCE' WHERE id=?",id);
        tx.executeWithoutResult(s->service.book(id,false));
        long booking=db.queryForObject("SELECT id FROM program_booking WHERE program_id=?",Long.class,id);
        tx.executeWithoutResult(s->service.book(id,true));login(1);
        assertThrows(BusinessException.class,()->tx.executeWithoutResult(s->service.reviewBooking(id,booking,"CONFIRM")));
    }
    @Test void onlyHostCanReadAttendeesAndCheckInWithinWindow(){
        long id=create(2);tx.executeWithoutResult(s->service.book(id,false));
        assertThrows(BusinessException.class,()->service.attendees(id));login(1);
        var attendee=service.attendees(id).get(0);long booking=Long.parseLong(attendee.get("id").toString());
        assertThrows(BusinessException.class,()->service.checkin(id,booking));
        db.update("UPDATE learning_program SET start_time=?,end_time=? WHERE id=?",LocalDateTime.now().minusMinutes(5),LocalDateTime.now().plusHours(1),id);
        tx.executeWithoutResult(s->service.checkin(id,booking));assertEquals("CHECKED_IN",service.attendees(id).get(0).get("status"));
    }

    // ── AU2（2026-11-04）：管理端按预约 ID 反查 ──

    @Test void adminBookingDetailReturnsProgramSummary(){
        long pid=create(10);
        tx.executeWithoutResult(s->service.book(pid,false));
        Long bookingId=db.queryForObject("SELECT id FROM program_booking WHERE program_id=? AND user_id=2",Long.class,pid);
        assertNotNull(bookingId);
        db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted) VALUES(9,'admin9','hash','管理员',3,1,1,0)");
        login(9);
        var detail=service.adminBookingDetail(bookingId);
        assertEquals(String.valueOf(bookingId),detail.get("id"),"雪花 ID 按字符串返回");
        assertEquals(String.valueOf(pid),detail.get("programId"));
        assertEquals("2",detail.get("userId"));
        assertEquals("BOOKED",detail.get("status"));
        var program=(Map<String,Object>)detail.get("program");
        assertNotNull(program,"反查必须带节目摘要");
        assertEquals("测试工作坊",program.get("title"));
        assertEquals("WORKSHOP",program.get("kind"));
        assertEquals("PUBLISHED",program.get("status"));
        assertEquals(String.valueOf(pid),program.get("id"));
    }

    @Test void adminBookingDetailMissingAndForbidden(){
        db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted) VALUES(9,'admin9','hash','管理员',3,1,1,0)");
        login(9);
        var notFound=assertThrows(BusinessException.class,()->service.adminBookingDetail(424242L));
        assertEquals(1004,notFound.getCode(),"不存在的预约应 1004");
        long pid=create(10);
        tx.executeWithoutResult(s->service.book(pid,false));
        Long bookingId=db.queryForObject("SELECT id FROM program_booking WHERE program_id=? AND user_id=2",Long.class,pid);
        login(2);
        var forbidden=assertThrows(BusinessException.class,()->service.adminBookingDetail(bookingId));
        assertEquals(1003,forbidden.getCode(),"学生/非管理员反查应 1003");
        db.update("UPDATE user SET role=1 WHERE id=2");
        var inheritor=assertThrows(BusinessException.class,()->service.adminBookingDetail(bookingId));
        assertEquals(1003,inheritor.getCode(),"传承人也不得有管理端反查权限");
    }
}
