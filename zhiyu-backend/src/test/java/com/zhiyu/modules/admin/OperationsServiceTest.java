package com.zhiyu.modules.admin;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.admin.service.OperationsService;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class OperationsServiceTest {
 JdbcTemplate db;OperationsService service;
 @BeforeEach void setup()throws Exception{var ds=new DriverManagerDataSource("jdbc:h2:mem:op"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER","sa","");try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new FileSystemResource("../sql/schema-h2.sql"));}db=new JdbcTemplate(ds);service=new OperationsService(db);db.update("INSERT INTO user(id,username,password,nickname,role,status,deleted) VALUES(1,'admin','hash','Admin',3,1,0),(2,'student','hash','Student',0,1,0)");UserContext.set(new LoginUser(1L,3));}
 @AfterEach void clear(){UserContext.clear();}
 @Test void emptyDaysAreZeroAndDistributionsAreEmpty(){var result=service.overview(7);var days=(List<?>)result.get("daily");assertEquals(7,days.size());assertEquals(0L,((Map<?,?>)days.get(0)).get("sessions"));assertTrue(((List<?>)result.get("sessionStates")).isEmpty());assertEquals(30,((List<?>)service.overview(30).get("daily")).size());}
 @Test void filtersDeletedRecordsAndSeparatesStartAndFinishDates(){var today=LocalDate.now(ZoneId.of("Asia/Shanghai"));db.update("INSERT INTO craft_project(id,inheritor_id,title,status,category,deleted) VALUES(11,1,'paper',1,'纸艺',0),(12,1,'hidden',1,'线',1)");db.update("INSERT INTO craft_session(id,user_id,project_id,status,current_step,start_time,end_time,deleted) VALUES(21,2,11,3,4,?,?,0)",today.minusDays(1).atStartOfDay(),today.atStartOfDay());var result=service.overview(7);var days=(List<?>)result.get("daily");assertEquals(1L,((Map<?,?>)days.get(5)).get("sessions"));assertEquals(0L,((Map<?,?>)days.get(5)).get("finished"));assertEquals(1L,((Map<?,?>)days.get(6)).get("finished"));assertEquals(1,((List<?>)result.get("categories")).size());assertFalse(result.toString().contains("userId"));}
 @Test void rejectsInvalidRangeAndNonAdmin(){assertThrows(BusinessException.class,()->service.overview(90));UserContext.set(new LoginUser(2L,3));assertThrows(BusinessException.class,()->service.overview(7));UserContext.clear();assertThrows(BusinessException.class,()->service.overview(7));}
}
