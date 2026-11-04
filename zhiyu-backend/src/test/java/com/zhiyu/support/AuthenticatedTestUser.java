package com.zhiyu.support;
import org.springframework.jdbc.core.JdbcTemplate;
/** Legacy controller fixtures must represent actual active users now that JWT roles are revalidated. */
public final class AuthenticatedTestUser {
 private AuthenticatedTestUser(){}
 public static void ensure(JdbcTemplate db,Long id,int role){
  ensure(db,id,role,1);
 }
 /** AE1 回归需要「已认证但未同意隐私政策」的夹具。 */
 public static void ensure(JdbcTemplate db,Long id,int role,int privacyAgreed){
  if(db.queryForObject("SELECT COUNT(*) FROM user WHERE id=?",Integer.class,id)==0)
   db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted) VALUES(?,?,'unused','测试账号',?,1,?,0,0)",id,"auth_fixture_"+id,role,privacyAgreed);
 }
}
