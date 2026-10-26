package com.zhiyu.modules;

import com.zhiyu.modules.campus.CampusService;
import com.zhiyu.modules.care.CareWorkflowService;
import com.zhiyu.security.*;
import com.zhiyu.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class BusinessWorkflowTest {
 @Autowired JdbcTemplate jdbc;
 @Autowired CampusService campus;
 @Autowired CareWorkflowService care;
 @Autowired BusinessAccess access;
 @Autowired MockMvc mvc;
 @Autowired JwtUtil jwt;
 @BeforeEach void seed(){
  for(int i=0;i<4;i++) jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted) VALUES(?,?,?,'test',?,1,1,0,0)",970000L+i,"workflow"+i,"unused",i);
  jdbc.update("INSERT INTO emotion_session(id,user_id,mood,status,deleted) VALUES(970010,970000,'calm',0,0)");
  jdbc.update("INSERT INTO care_reminder(id,user_id,risk_level,summary,status,deleted) VALUES(970020,970000,1,'脱敏提醒',0,0)");
  UserContext.set(new LoginUser(970000L,0));
 }
 @AfterEach void clear(){UserContext.clear();}
 void actor(long id,int role){UserContext.set(new LoginUser(id,role));}
 String token(long id,int role){return "Bearer "+jwt.createToken(id,role);}
 @Test void campusEncryptsAndReviewsOnce(){
  campus.apply(new CampusService.Application("江西师范大学","人工智能学院","TEST970000","张同学"));
  String cipher=jdbc.queryForObject("SELECT identity_cipher FROM campus_verification WHERE user_id=970000",String.class);
  assertFalse(cipher.contains("TEST970000"));assertFalse(cipher.contains("张同学"));
  assertEquals("PENDING",campus.mine().get("status"));
  assertThrows(BusinessException.class,()->campus.apply(new CampusService.Application("学校","学院","123","姓名")));
  long id=jdbc.queryForObject("SELECT id FROM campus_verification WHERE user_id=970000",Long.class);
  actor(970003,3);campus.review(id,new CampusService.Review("APPROVED","已核实"));
  assertThrows(BusinessException.class,()->campus.review(id,new CampusService.Review("REJECTED","重复")));
  assertEquals(0,jdbc.queryForObject("SELECT role FROM user WHERE id=970000",Integer.class));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM admin_operation_log WHERE target_id=?",Integer.class,id));
 }
 @Test void campusRequiresConsentAndAdmin(){
  jdbc.update("UPDATE user SET privacy_agreed=0 WHERE id=970000");
  assertThrows(BusinessException.class,()->campus.apply(new CampusService.Application("学校","学院","123","姓名")));
  actor(970001,1);assertThrows(BusinessException.class,()->campus.list(1));
 }
 @Test void careEnforcesClaimConsentAndState(){
  actor(970002,2);care.follow(970020L,"CLAIM");
  assertThrows(BusinessException.class,()->care.follow(970020L,"CLAIM"));
  assertThrows(BusinessException.class,()->care.follow(970020L,"CONTACTED"));
  jdbc.update("UPDATE user SET can_contact=1 WHERE id=970000");care.follow(970020L,"CONTACTED");
  jdbc.update("UPDATE user SET can_contact=0 WHERE id=970000");assertThrows(BusinessException.class,()->care.follow(970020L,"CONTACTED"));
  care.follow(970020L,"CLOSE");assertThrows(BusinessException.class,()->care.follow(970020L,"REVIEWED"));
  care.follow(970020L,"REOPEN");assertEquals("OPEN",care.detail(970020L).get("state"));
  assertFalse(care.detail(970020L).containsKey("user_id"));
  actor(970001,1);assertThrows(BusinessException.class,()->care.detail(970020L));
 }
 @Test void ownershipRejectsOtherUser(){
  access.emotionSession(970010L,970000L);
  assertThrows(BusinessException.class,()->access.emotionSession(970010L,970001L));
 }
 @Test void disabledTokenRejected() throws Exception {
  jdbc.update("UPDATE user SET status=0 WHERE id=970000");
  mvc.perform(get("/api/emotion/sessions").header("Authorization",token(970000,0))).andExpect(status().isUnauthorized());
 }
 @Test void roleUsesDatabaseNotStaleToken() throws Exception {
  mvc.perform(get("/api/admin/campus-verifications").header("Authorization",token(970000,3))).andExpect(status().isForbidden());
 }
 @Test void cannotReadOrChatInForeignSession() throws Exception {
  mvc.perform(get("/api/emotion/sessions/970010").header("Authorization",token(970001,1))).andExpect(status().isNotFound());
  mvc.perform(post("/api/emotion/chat").header("Authorization",token(970001,1)).contentType(MediaType.APPLICATION_JSON).content("{\"sessionId\":970010,\"content\":\"你好\"}")).andExpect(status().isNotFound());
 }
 @Test void withdrawalStopsChatBeforeAsync() throws Exception {
  jdbc.update("UPDATE user SET privacy_agreed=0 WHERE id=970000");
  mvc.perform(post("/api/emotion/chat").header("Authorization",token(970000,0)).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"你好\"}")).andExpect(status().isForbidden());
 }
 @Test void inheritorCannotAccessCare() throws Exception {
  mvc.perform(get("/api/admin/care-reminders").header("Authorization",token(970001,1))).andExpect(status().isForbidden());
 }
 @Test void deletingHistoryRemovesMessages() throws Exception {
  jdbc.update("INSERT INTO emotion_message(id,session_id,role,content,deleted) VALUES(970011,970010,'user','encrypted',0)");
  mvc.perform(delete("/api/emotion/sessions/970010").header("Authorization",token(970000,0))).andExpect(status().isOk());
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM emotion_message WHERE session_id=970010",Integer.class));
  assertEquals(1,jdbc.queryForObject("SELECT deleted FROM emotion_session WHERE id=970010",Integer.class));
 }
}
