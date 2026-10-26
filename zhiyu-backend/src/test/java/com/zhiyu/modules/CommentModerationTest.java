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
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test") @Transactional
class CommentModerationTest {
 @Autowired org.mybatis.spring.SqlSessionTemplate session;@Autowired JdbcTemplate db;@Autowired CommentModerationService moderation;@Autowired WorkInteractionService interactions;
 @BeforeEach void setup(){for(int i=0;i<3;i++)db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted) VALUES(?,?,'unused','测试评论用户',?,1,1,0)",997000+i,"commentqa"+i,i==2?3:0);db.update("INSERT INTO work(id,user_id,title,media_type,visible,anonymous,like_count,status,deleted) VALUES(997010,997000,'测试作品','text',1,0,0,1,0)");actor(997000);}
 void actor(long id){session.clearCache();UserContext.set(new LoginUser(id,id==997002?3:0));}
 @AfterEach void cleanup(){UserContext.clear();}
 long submit(){return interactions.comment(997000L,997010L,"一针一线，慢慢完成").getId();}
 @Test void pendingIsPrivateThenApprovalPublishesAndLogs(){long id=submit();var own=interactions.listComments(997010L,1,10);assertEquals(0,own.getRecords().get(0).getStatus());assertTrue(own.getRecords().get(0).isMine());actor(997001);assertEquals(0,interactions.listComments(997010L,1,10).getTotal());actor(997002);moderation.review(id,1,0);assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM admin_operation_log WHERE target_id=?",Integer.class,id));actor(997001);assertEquals(1,interactions.listComments(997010L,1,10).getTotal());}
 @Test void hiddenRemainsVisibleOnlyToAuthor(){long id=submit();actor(997002);moderation.review(id,2,0);actor(997001);assertEquals(0,interactions.listComments(997010L,1,10).getTotal());actor(997000);assertEquals(2,interactions.listComments(997010L,1,10).getRecords().get(0).getStatus());}
 @Test void withdrawalErasesBodyAndPreventsStaleReview(){long id=submit();actor(997001);assertThrows(BusinessException.class,()->moderation.remove(997010,id));actor(997000);moderation.remove(997010,id);assertEquals("",db.queryForObject("SELECT content FROM work_comment WHERE id=?",String.class,id));actor(997002);assertThrows(BusinessException.class,()->moderation.review(id,1,0));}
 @Test void onlyAdminCanReviewAndRepeatedActionFails(){long id=submit();assertThrows(BusinessException.class,()->moderation.review(id,1,0));actor(997002);moderation.review(id,1,0);assertThrows(BusinessException.class,()->moderation.review(id,2,0));}
 @Test void privateAndHiddenWorksDenyInteraction(){submit();db.update("UPDATE work SET visible=0 WHERE id=997010");actor(997001);assertThrows(BusinessException.class,()->interactions.listComments(997010L,1,10));assertThrows(BusinessException.class,()->interactions.comment(997001L,997010L,"你好"));assertThrows(BusinessException.class,()->interactions.toggleLike(997001L,997010L,1));db.update("UPDATE work SET status=2 WHERE id=997010");actor(997000);assertThrows(BusinessException.class,()->interactions.listComments(997010L,1,10));}
}
