package com.zhiyu.modules;

import com.zhiyu.modules.work.service.*;
import com.zhiyu.security.*;
import com.zhiyu.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mybatis.spring.SqlSessionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class WorkSharingTest {
 @Autowired JdbcTemplate db;
 @Autowired PersonalWorkService personal;
 @Autowired WorkInteractionService interactions;
 @Autowired WorkService works;
 @Autowired SqlSessionTemplate sqlSession;
 @BeforeEach void setup(){
  for(int i=0;i<2;i++)db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted) VALUES(?,?,'unused','分享测试',0,1,1,0)",997000+i,"shareqa"+i);
  for(int i=0;i<4;i++)db.update("INSERT INTO work(id,user_id,title,media_type,visible,anonymous,like_count,status,create_time,deleted) VALUES(?,997000,'测试作品','text',?,1,0,?,CURRENT_TIMESTAMP,0)",997010+i,i==1?0:i==2?2:1,i==3?2:1);
  UserContext.set(new LoginUser(997001L,0));
 }
 @AfterEach void clear(){UserContext.clear();}
 @Test void detailRestoresOwnInteractionStateAndShareability(){
  assertFalse(works.getWork(997001L,997010L).getLiked());
  interactions.toggleLike(997001L,997010L,1);interactions.toggleLike(997001L,997010L,0);
  var detail=works.getWork(997001L,997010L);assertTrue(detail.getLiked());assertTrue(detail.getFavorited());assertTrue(detail.getShareable());assertEquals("匿名同学",detail.getAuthorName());
  var other=works.getWork(997000L,997010L);assertFalse(other.getLiked());assertFalse(other.getFavorited());
 }
 @Test void privateGroupAndDownWorksCannotBeOpenedByRecipient(){
  for(long id=997011;id<=997013;id++){final long workId=id;assertEquals(1004,assertThrows(BusinessException.class,()->works.getWork(997001L,workId)).getCode());}
  assertFalse(works.getWork(997000L,997011L).getShareable());assertFalse(works.getWork(997000L,997012L).getShareable());
 }
 @Test void favoritesFilterVisibilityAndOtherUsersAndRemovalIsIdempotent(){
  for(int i=0;i<4;i++)db.update("INSERT INTO work_like(id,user_id,work_id,type,deleted) VALUES(?,997001,?,0,0)",997100+i,997010+i);
  db.update("INSERT INTO work_like(id,user_id,work_id,type,deleted) VALUES(997200,997000,997010,0,0)");
  assertEquals(1L,personal.favorites(1).get("total"));
  personal.unfavorite(997010);personal.unfavorite(997010);
  assertEquals(0L,personal.favorites(1).get("total"));
  assertEquals(0,db.queryForObject("SELECT deleted FROM work_like WHERE id=997200",Integer.class));
  personal.unfavorite(997013);assertEquals(1,db.queryForObject("SELECT deleted FROM work_like WHERE id=997103",Integer.class));
 }
 @Test void withdrawnWorkDisappearsFromFavoritesAndOldLink(){
  interactions.toggleLike(997001L,997010L,0);UserContext.set(new LoginUser(997000L,0));personal.withdraw(997010);
  sqlSession.clearCache();UserContext.set(new LoginUser(997001L,0));
  assertEquals(0L,personal.favorites(1).get("total"));assertThrows(BusinessException.class,()->works.getWork(997001L,997010L));
 }
 @Test void favoritesPaginationAndInvalidPage(){
  for(int i=0;i<13;i++){db.update("INSERT INTO work(id,user_id,title,media_type,visible,anonymous,like_count,status,deleted) VALUES(?,997000,'分页作品','text',1,1,0,1,0)",997300+i);db.update("INSERT INTO work_like(id,user_id,work_id,type,deleted) VALUES(?,997001,?,0,0)",997400+i,997300+i);}
  var first=(List<Map<String,Object>>)personal.favorites(1).get("records");var second=(List<Map<String,Object>>)personal.favorites(2).get("records");
  assertEquals(12,first.size());assertEquals(1,second.size());assertTrue(first.stream().allMatch(r->r.get("id") instanceof String && r.get("id").toString().matches("[0-9]+")));assertFalse(first.stream().anyMatch(r->r.get("id").equals(second.get(0).get("id"))));assertThrows(BusinessException.class,()->personal.favorites(0));
 }
}
