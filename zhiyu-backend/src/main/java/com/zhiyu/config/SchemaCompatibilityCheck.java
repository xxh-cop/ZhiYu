package com.zhiyu.config;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.util.*;

/** Fail early with a repair path rather than allowing production pages to fail later. */
@Component
@Profile("!test")
public class SchemaCompatibilityCheck implements ApplicationRunner {
 private final DataSource source;
 public SchemaCompatibilityCheck(DataSource source){this.source=source;}
 @Override public void run(ApplicationArguments args) throws Exception {
  try(var connection=source.getConnection()){
   var required=new HashMap<>(Map.of("work",List.of("media_type","video_url"),"craft_session",List.of("emotion_session_id"),"craft_project",List.of("video_url"),"media_asset",List.of("storage_key","user_id"),"campus_verification",List.of("identity_cipher","status"),"care_case",List.of("owner_id","state"),"care_followup",List.of("reminder_id","action"),"admin_operation_log",List.of("actor_id","action"),"course_lesson",List.of("program_id","published"),"lesson_progress",List.of("lesson_id","user_id","completed")));required.put("group_message",List.of("group_id","user_id","content"));for(String table:List.of("program_collaborator","guidance_channel","guidance_message","group_companion","group_work","runtime_config","file_cleanup_job"))required.put(table,List.of("id","create_time","update_time","deleted"));var missing=new ArrayList<String>();
   for(var entry:required.entrySet()){var actual=new HashSet<String>();try(var rows=connection.getMetaData().getColumns(connection.getCatalog(),null,entry.getKey(),null)){while(rows.next())actual.add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));}for(var field:entry.getValue())if(!actual.contains(field))missing.add(entry.getKey()+"."+field);}
   if(!missing.isEmpty())throw new IllegalStateException("数据库结构尚未升级，缺少字段："+String.join(", ",missing)+"。请按顺序执行 sql/migrations/ 下的增量迁移（包括 20261015_business_workflows.sql 和 20261017_course_lessons.sql、20261018_group_messages.sql、20261020_final_business.sql）后重新启动。仅执行 CREATE TABLE IF NOT EXISTS 不会升级已有表。");
  }
 }
}
