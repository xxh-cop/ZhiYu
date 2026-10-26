package com.zhiyu.modules;
import com.zhiyu.modules.group.service.*;
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
class GroupCommunityTest {
 @Autowired JdbcTemplate db;@Autowired GroupCommunityService community;@Autowired GroupService groups;
 @BeforeEach void seed(){for(int i=0;i<3;i++)db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted) VALUES(?,?,'unused','测试成员',0,1,1,0)",990000+i,"groupqa"+i);db.update("INSERT INTO support_group(id,name,description,max_count,member_count,status,deleted) VALUES(990010,'测试小组','组内交流',10,2,1,0)");db.update("INSERT INTO group_member(id,group_id,user_id,role,deleted) VALUES(990020,990010,990000,'组长',0),(990021,990010,990001,'成员',0)");actor(990001);}
 @AfterEach void clear(){UserContext.clear();}void actor(long id){UserContext.set(new LoginUser(id,0));}
 @Test void encryptedMessageAndMembership(){community.send(990010,"今天完成了手作");assertFalse(db.queryForObject("SELECT content FROM group_message WHERE group_id=990010",String.class).contains("手作"));assertEquals("今天完成了手作",community.messages(990010,null).get(0).get("content"));actor(990002);assertThrows(BusinessException.class,()->community.messages(990010,null));}
 @Test void leaveRejoinRestoresMembership(){community.leave(990010);assertThrows(BusinessException.class,()->community.detail(990010));groups.joinGroup(990001L,990010L);assertEquals(2,db.queryForObject("SELECT member_count FROM support_group WHERE id=990010",Integer.class));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM group_member WHERE group_id=990010 AND user_id=990001",Integer.class));}
 @Test void leaderMustTransfer(){actor(990000);assertThrows(BusinessException.class,()->community.leave(990010));community.transfer(990010,990021);community.leave(990010);actor(990001);assertEquals("组长",community.detail(990010).get("myRole"));}
 @Test void onlyAuthorOrLeaderCanRemove(){actor(990000);community.send(990010,"欢迎大家");long id=db.queryForObject("SELECT id FROM group_message WHERE group_id=990010",Long.class);actor(990001);assertThrows(BusinessException.class,()->community.remove(990010,id));actor(990000);community.remove(990010,id);assertTrue(community.messages(990010,null).isEmpty());assertEquals("",db.queryForObject("SELECT content FROM group_message WHERE id=?",String.class,id));}
 @Test void lastMemberLeavingClosesGroup(){community.leave(990010);actor(990000);community.leave(990010);assertEquals(0,db.queryForObject("SELECT status FROM support_group WHERE id=990010",Integer.class));assertEquals(0,db.queryForObject("SELECT member_count FROM support_group WHERE id=990010",Integer.class));}
 @Test void closedGroupCannotSend(){db.update("UPDATE support_group SET status=0 WHERE id=990010");assertThrows(BusinessException.class,()->community.send(990010,"你好"));}
}
