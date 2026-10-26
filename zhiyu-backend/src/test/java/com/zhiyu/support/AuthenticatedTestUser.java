package com.zhiyu.support;
import org.springframework.jdbc.core.JdbcTemplate;
/** Legacy controller fixtures must represent actual active users now that JWT roles are revalidated. */
public final class AuthenticatedTestUser {
 private AuthenticatedTestUser(){}
 public static void ensure(JdbcTemplate db,Long id,int role){
  if(db.queryForObject("SELECT COUNT(*) FROM user WHERE id=?",Integer.class,id)==0)
   db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted) VALUES(?,?,'unused','测试账号',?,1,1,0,0)",id,"auth_fixture_"+id,role);
 }
}
