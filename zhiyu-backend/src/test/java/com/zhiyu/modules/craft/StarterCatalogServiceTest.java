package com.zhiyu.modules.craft;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.craft.service.StarterCatalogService;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ClassPathResource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StarterCatalogServiceTest {
 JdbcTemplate db;StarterCatalogService service;List<Map<String,Object>> catalog;
 @BeforeEach void setup() throws Exception {
  var ds=new DriverManagerDataSource("jdbc:h2:mem:starter"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER","sa","");
  try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new FileSystemResource("../sql/schema-h2.sql"));}
  db=new JdbcTemplate(ds);var json=new ObjectMapper();service=new StarterCatalogService(db,json);
  try(var input=new ClassPathResource("catalog/starter-crafts.json").getInputStream()){catalog=json.readValue(input,new TypeReference<>(){});}
  db.update("INSERT INTO user(id,username,password,nickname,role,status,deleted) VALUES(1,'a','hash','测试甲',0,1,0),(2,'b','hash','测试乙',0,1,0)");
  UserContext.set(new LoginUser(1L,0));
 }
 @AfterEach void cleanup(){UserContext.clear();}
 void seed(int index){var d=catalog.get(index);long id=Long.parseLong(d.get("id").toString());
  db.update("INSERT INTO craft_project(id,inheritor_id,title,est_minutes,status,deleted) VALUES(?,1,?,?,1,0)",id,d.get("title"),d.get("minutes"));
  int n=0;for(var v:(List<?>)d.get("steps")){var s=(Map<?,?>)v;n++;db.update("INSERT INTO craft_step(id,project_id,step_no,title,guide,deleted) VALUES(?,?,?,?,?,0)",id*100+n,id,n,s.get("title"),s.get("guide")+"\n遇到困难："+s.get("rescue"));}
  n=0;for(var v:(List<?>)d.get("materials")){var m=(Map<?,?>)v;n++;db.update("INSERT INTO craft_material(id,project_id,name,qty,deleted) VALUES(?,?,?,?,0)",id*100+50+n,id,m.get("name"),m.get("qty"));}
 }
 @Test void twelveCompleteGuidesHaveSourcesAndRecoveryAdvice(){
  assertEquals(12,catalog.size());var ids=new HashSet<String>();
  for(var d:catalog){assertTrue(ids.add(d.get("id").toString()));assertEquals(4,((List<?>)d.get("steps")).size());assertFalse(d.get("authorship").toString().isBlank());
   for(var s:(List<?>)d.get("steps"))assertFalse(((Map<?,?>)s).get("rescue").toString().isBlank());}
 }
 @Test void onlyPublishedNondeletedCompleteGuidesAreReturned(){
  seed(0);seed(1);seed(2);db.update("UPDATE craft_project SET status=0 WHERE id=940002");db.update("UPDATE craft_project SET deleted=1 WHERE id=940003");
  assertEquals(1,service.available().size());assertEquals("940001",service.available().get(0).get("id"));
 }
 @Test void editorChangesHideOutdatedPreparation(){
  seed(0);db.update("UPDATE craft_step SET guide='edited' WHERE project_id=940001 AND step_no=1");assertTrue(service.available().isEmpty());
  seed(1);db.update("UPDATE craft_material SET qty='changed' WHERE project_id=940002");assertTrue(service.available().isEmpty());
 }
 @Test void activeSessionsAreOwnedPublishedAndPreserveLargeIds(){
  seed(0);seed(1);db.update("UPDATE craft_project SET status=0 WHERE id=940002");
  db.update("INSERT INTO craft_session(id,user_id,project_id,status,current_step,deleted) VALUES(9007199254740993,1,940001,2,3,0),(22,2,940001,1,1,0),(23,1,940001,3,4,0),(24,1,940002,1,1,0)");
  var rows=service.active(null);assertEquals(1,rows.size());assertEquals("9007199254740993",rows.get(0).get("sessionId"));assertEquals(3,rows.get(0).get("currentStep"));assertTrue(service.active(940002L).isEmpty());
 }
 @Test void authenticationAndLiveAccountStatusAreRequired(){
  UserContext.clear();assertThrows(BusinessException.class,()->service.available());UserContext.set(new LoginUser(1L,0));db.update("UPDATE user SET status=0 WHERE id=1");assertThrows(BusinessException.class,()->service.active(null));
 }
 @Test void aggregateIsAdminOnlyAndEmptyRateIsUnknown(){
  assertThrows(BusinessException.class,()->service.quality());db.update("UPDATE user SET role=3 WHERE id=1");
  assertNull(service.quality().get("completionRate"));seed(0);
  db.update("INSERT INTO craft_session(id,user_id,project_id,status,current_step,deleted) VALUES(1,1,940001,3,4,0),(2,2,940001,2,2,0),(3,2,940001,4,1,1)");
  var q=service.quality();assertEquals(2L,q.get("started"));assertEquals(1L,q.get("finished"));assertEquals(50.0,q.get("completionRate"));assertFalse(q.containsKey("userId"));
 }
}
