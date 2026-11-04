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
   var required=new HashMap<>(Map.of("work",List.of("media_type","video_url","audit_reason"),"craft_session",List.of("emotion_session_id"),"craft_project",List.of("video_url","audit_reason"),"media_asset",List.of("storage_key","user_id"),"campus_verification",List.of("identity_cipher","status"),"care_case",List.of("owner_id","state"),"care_followup",List.of("reminder_id","action"),"admin_operation_log",List.of("actor_id","action"),"course_lesson",List.of("program_id","published"),"lesson_progress",List.of("lesson_id","user_id","completed")));required.put("work_comment",List.of("audit_reason"));required.put("practice_session",List.of("id","user_id","kind","status","minutes_budget","current_step","create_time","update_time","deleted"));required.put("user_ai_memory",List.of("id","user_id","memory_key","value_cipher","consent_version","create_time","update_time","deleted"));required.put("ai_trace",List.of("id","trace_id","scene","result_status","create_time"));required.put("group_message",List.of("group_id","user_id","content"));required.put("ai_audit_log",List.of("user_id","ref_type","ref_id"));required.put("craft_recommend_event",List.of("id","user_id","recommendation_id","project_id","event_type","source","create_time","update_time","deleted"));required.put("heritage_catalog_version",List.of("id","version_no","payload","note","actor_id","create_time","update_time","deleted"));required.put("notification",List.of("id","user_id","type","title","ref_id","sub_ref_id","dedupe_key","read_flag","create_time","update_time","deleted"));required.put("dialogue_context_summary",List.of("id","user_id","session_id","covered_end_turn","summary_cipher","create_time","update_time","deleted"));required.put("crisis_session_log",List.of("id","user_id","scene","risk_level","summary_cipher","create_time","update_time","deleted"));required.put("user",List.of("baseline_nudge_enabled","baseline_nudge_last_at","baseline_nudge_last_level"));required.put("admin_export_log",List.of("id","actor_id","target_type","field_list","batch_no","create_time"));for(String table:List.of("program_collaborator","guidance_channel","guidance_message","group_companion","group_work","runtime_config","file_cleanup_job"))required.put(table,List.of("id","create_time","update_time","deleted"));var missing=new ArrayList<String>();
   for(var entry:required.entrySet()){var actual=new HashSet<String>();try(var rows=connection.getMetaData().getColumns(connection.getCatalog(),null,entry.getKey(),null)){while(rows.next())actual.add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));}for(var field:entry.getValue())if(!actual.contains(field))missing.add(entry.getKey()+"."+field);}
   if(!missing.isEmpty())throw new IllegalStateException("数据库结构尚未升级，缺少字段："+String.join(", ",missing)+"。请按顺序执行 sql/migrations/ 下的增量迁移（包括 20261015_business_workflows.sql 和 20261017_course_lessons.sql、20261018_group_messages.sql、20261020_final_business.sql、20261026_ai_audit_user_id.sql、20261028_craft_recommend_event.sql、20261030_l4_companion.sql、20261031_emotion_summary_text.sql、20261031_heritage_content_workflow.sql、20261104_notification.sql、20261104_audit_reason_ref.sql、20261104_admin_export_log.sql、20261104_notification_sub_ref.sql、20261104_dialogue_context_summary.sql、20261104_crisis_session_log.sql、20261104_user_baseline_nudge.sql、20261105_craft_project_audit_reason.sql）后重新启动。仅执行 CREATE TABLE IF NOT EXISTS 不会升级已有表。");
   var summaryType=new String[1];
   try(var rows=connection.getMetaData().getColumns(connection.getCatalog(),null,"emotion_session",null)){while(rows.next())if("summary".equalsIgnoreCase(rows.getString("COLUMN_NAME")))summaryType[0]=rows.getString("TYPE_NAME");}
   if(summaryType[0]==null||!summaryType[0].toLowerCase(Locale.ROOT).contains("text"))throw new IllegalStateException("emotion_session.summary 仍是短字符列，加密后的会话总结会写入失败。请执行 sql/migrations/20261031_emotion_summary_text.sql 后重新启动。");
  }
 }
}
