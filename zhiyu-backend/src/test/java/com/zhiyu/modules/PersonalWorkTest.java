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
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test") @Transactional
class PersonalWorkTest {
 @Autowired JdbcTemplate db;@Autowired PersonalWorkService personal;@Autowired WorkInteractionService interactions;@Autowired WorkService works;@Autowired com.zhiyu.modules.file.MediaService media;
 @BeforeEach void setup(){for(int i=0;i<2;i++)db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted) VALUES(?,?,'unused','测试作品用户',0,1,1,0)",998000+i,"personalqa"+i);for(int i=0;i<3;i++)db.update("INSERT INTO work(id,user_id,title,media_type,visible,anonymous,like_count,status,create_time,deleted) VALUES(?,998000,'测试作品','text',?,0,0,?,CURRENT_TIMESTAMP,0)",998010+i,i==1?0:1,i);db.update("INSERT INTO work(id,user_id,title,media_type,visible,anonymous,like_count,status,deleted) VALUES(998020,998001,'其他人作品','text',1,0,0,1,0)");UserContext.set(new LoginUser(998000L,0));}
 @AfterEach void clear(){UserContext.clear();}
 @Test void ownScopeAndStatusCounts(){var result=personal.mine(1,null);assertEquals(3L,result.get("total"));assertEquals(1L,personal.mine(1,0).get("total"));assertEquals(1L,personal.mine(1,2).get("total"));var records=(List<Map<String,Object>>)result.get("records");assertTrue(records.stream().allMatch(r->r.get("id") instanceof String));assertFalse(records.stream().anyMatch(r->r.get("id").equals("998020")));}
 @Test void cannotWithdrawAnotherUsersWork(){assertThrows(BusinessException.class,()->personal.withdraw(998020));assertEquals(0,db.queryForObject("SELECT deleted FROM work WHERE id=998020",Integer.class));}
 @Test void withdrawalErasesCommentsAndInteractions(){db.update("INSERT INTO work_comment(id,work_id,user_id,content,status,deleted) VALUES(998100,998011,998001,'私密留言',1,0)");db.update("INSERT INTO work_like(id,work_id,user_id,type,deleted) VALUES(998101,998011,998001,1,0)");personal.withdraw(998011);assertEquals(2L,personal.mine(1,null).get("total"));assertEquals("",db.queryForObject("SELECT content FROM work_comment WHERE id=998100",String.class));assertEquals(1,db.queryForObject("SELECT deleted FROM work_like WHERE id=998101",Integer.class));assertThrows(BusinessException.class,()->personal.withdraw(998011));assertThrows(BusinessException.class,()->works.getWork(998000L,998011L));assertThrows(BusinessException.class,()->interactions.comment(998000L,998011L,"新评论"));}
 @Test void invalidFiltersFail(){assertThrows(BusinessException.class,()->personal.mine(0,null));assertThrows(BusinessException.class,()->personal.mine(1,3));}
 @Test void withdrawnVideoRevokesOtherUsersSignedPlayback(){
  db.update("INSERT INTO media_asset(id,user_id,storage_key,size_bytes,create_time,update_time,deleted) VALUES(998200,998000,'unused-test.mp4',16,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)");
  db.update("UPDATE work SET visible=1,media_type='video',video_url='/api/media/998200/content' WHERE id=998011");
  String link=media.playback(998001L,998200);Map<String,String> parameters=new HashMap<>();for(String pair:link.substring(link.indexOf('?')+1).split("&")){String[] parts=pair.split("=",2);parameters.put(parts[0],parts[1]);}
  personal.withdraw(998011);
  assertEquals(1003,assertThrows(BusinessException.class,()->media.playback(998001L,998200)).getCode());
  assertEquals(1003,assertThrows(BusinessException.class,()->media.content(998200,998001L,Long.parseLong(parameters.get("expires")),parameters.get("signature"))).getCode());
  assertNotNull(media.playback(998000L,998200));
 }
}
