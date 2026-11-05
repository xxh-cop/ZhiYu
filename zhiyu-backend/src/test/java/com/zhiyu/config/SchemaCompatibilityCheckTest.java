package com.zhiyu.config;

import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchemaCompatibilityCheckTest {
 @Test void legacySchemaReportsRepairPath() throws Exception {
  var check=new SchemaCompatibilityCheck(source(false));
  var error=assertThrows(IllegalStateException.class,()->check.run(null));
  assertTrue(error.getMessage().contains("work.media_type"));
  assertTrue(error.getMessage().contains("sql/migrations/"));
  assertTrue(error.getMessage().contains("20261017_course_lessons.sql"));
  assertTrue(error.getMessage().contains("20261026_ai_audit_user_id.sql"));
  assertTrue(error.getMessage().contains("20261028_craft_recommend_event.sql"));
 }
 @Test void upgradedSchemaCanStart() throws Exception {
  assertDoesNotThrow(()->new SchemaCompatibilityCheck(source(true)).run(null));
 }
 private DataSource source(boolean upgraded) throws Exception {
  var source=mock(DataSource.class);var connection=mock(Connection.class);var metadata=mock(DatabaseMetaData.class);
  when(source.getConnection()).thenReturn(connection);when(connection.getCatalog()).thenReturn("zhiyu");when(connection.getMetaData()).thenReturn(metadata);
  for(var entry:java.util.Map.of("group_message",java.util.List.of("group_id","user_id","content"),"campus_verification",java.util.List.of("identity_cipher","status"),"care_case",java.util.List.of("owner_id","state"),"care_followup",java.util.List.of("reminder_id","action"),"admin_operation_log",java.util.List.of("actor_id","action"),"course_lesson",java.util.List.of("program_id","published"),"lesson_progress",java.util.List.of("lesson_id","user_id","completed")).entrySet()){
   var rows=mock(ResultSet.class);var position=new java.util.concurrent.atomic.AtomicInteger(-1);
   when(rows.next()).thenAnswer(i->position.incrementAndGet()<entry.getValue().size());
   when(rows.getString("COLUMN_NAME")).thenAnswer(i->entry.getValue().get(position.get()));
   when(metadata.getColumns("zhiyu",null,entry.getKey(),null)).thenReturn(rows);
  }
  for(String table:java.util.List.of("program_collaborator","guidance_channel","guidance_message","group_companion","group_work","runtime_config","file_cleanup_job")){var rows=mock(ResultSet.class);when(rows.next()).thenReturn(true,true,true,true,false);when(rows.getString("COLUMN_NAME")).thenReturn("id","create_time","update_time","deleted");when(metadata.getColumns("zhiyu",null,table,null)).thenReturn(rows);}
  var work=mock(ResultSet.class);when(work.next()).thenReturn(true,upgraded,upgraded,false);when(work.getString("COLUMN_NAME")).thenReturn(upgraded?"media_type":"id","video_url","audit_reason");
  var comment=mock(ResultSet.class);when(comment.next()).thenReturn(upgraded,false);when(comment.getString("COLUMN_NAME")).thenReturn("audit_reason");when(metadata.getColumns("zhiyu",null,"work_comment",null)).thenReturn(comment);
  var session=mock(ResultSet.class);when(session.next()).thenReturn(true,false);when(session.getString("COLUMN_NAME")).thenReturn("emotion_session_id");
  var project=mock(ResultSet.class);var projectPos=new java.util.concurrent.atomic.AtomicInteger(-1);var projectCols=upgraded?java.util.List.of("video_url","audit_reason","takedown_kind","takedown_at","archive_at","export_until","notice_key"):java.util.List.of("video_url");when(project.next()).thenAnswer(i->projectPos.incrementAndGet()<projectCols.size());when(project.getString("COLUMN_NAME")).thenAnswer(i->projectCols.get(projectPos.get()));var media=mock(ResultSet.class);when(media.next()).thenReturn(true,true,false);when(media.getString("COLUMN_NAME")).thenReturn("storage_key","user_id");when(metadata.getColumns("zhiyu",null,"craft_project",null)).thenReturn(project);when(metadata.getColumns("zhiyu",null,"media_asset",null)).thenReturn(media);
  // 2026-11-05 分层下架：craft_step 音频资产三字段
  var stepCols=upgraded?java.util.List.of("license_status","attribution","asset_status"):java.util.List.<String>of();
  var step=mock(ResultSet.class);var stepPos=new java.util.concurrent.atomic.AtomicInteger(-1);
  when(step.next()).thenAnswer(i->stepPos.incrementAndGet()<stepCols.size());
  when(step.getString("COLUMN_NAME")).thenAnswer(i->stepCols.get(stepPos.get()));
  when(metadata.getColumns("zhiyu",null,"craft_step",null)).thenReturn(step);
  var auditLog=mock(ResultSet.class);when(auditLog.next()).thenReturn(upgraded,upgraded,upgraded,false);when(auditLog.getString("COLUMN_NAME")).thenReturn("user_id","ref_type","ref_id");when(metadata.getColumns("zhiyu",null,"ai_audit_log",null)).thenReturn(auditLog);
  var eventColumns=upgraded?java.util.List.of("id","user_id","recommendation_id","project_id","event_type","source","create_time","update_time","deleted"):java.util.List.<String>of();
  var event=mock(ResultSet.class);var eventPosition=new java.util.concurrent.atomic.AtomicInteger(-1);
  when(event.next()).thenAnswer(i->eventPosition.incrementAndGet()<eventColumns.size());
  when(event.getString("COLUMN_NAME")).thenAnswer(i->eventColumns.get(eventPosition.get()));
  when(metadata.getColumns("zhiyu",null,"craft_recommend_event",null)).thenReturn(event);
  // 2026-11-05 心流度量配套：craft_session_event 必检表
  var sessionEventCols=upgraded?java.util.List.of("id","session_id","user_id","craft_project_id","event_type","occurred_at","create_time","update_time","deleted"):java.util.List.<String>of();
  var sessionEvent=mock(ResultSet.class);var sessionEventPos=new java.util.concurrent.atomic.AtomicInteger(-1);
  when(sessionEvent.next()).thenAnswer(i->sessionEventPos.incrementAndGet()<sessionEventCols.size());
  when(sessionEvent.getString("COLUMN_NAME")).thenAnswer(i->sessionEventCols.get(sessionEventPos.get()));
  when(metadata.getColumns("zhiyu",null,"craft_session_event",null)).thenReturn(sessionEvent);
  when(metadata.getColumns("zhiyu",null,"work",null)).thenReturn(work);when(metadata.getColumns("zhiyu",null,"craft_session",null)).thenReturn(session);
  // L4 批次新增必检表（practice_session / user_ai_memory / ai_trace / heritage_catalog_version）
  // 2026-11-04 通知系统新增必检表 notification
  for(var entry:java.util.Map.of(
    "practice_session",java.util.List.of("id","user_id","kind","status","minutes_budget","current_step","create_time","update_time","deleted"),
    "user_ai_memory",java.util.List.of("id","user_id","memory_key","value_cipher","consent_version","create_time","update_time","deleted"),
    "ai_trace",java.util.List.of("id","trace_id","user_id","scene","model","latency_ms","input_tokens","output_tokens","result_status","fallback_reason","create_time"),
    "notification",java.util.List.of("id","user_id","type","title","ref_id","sub_ref_id","dedupe_key","read_flag","create_time","update_time","deleted"),
    "dialogue_context_summary",java.util.List.of("id","user_id","session_id","covered_end_turn","summary_cipher","create_time","update_time","deleted"),
    "crisis_session_log",java.util.List.of("id","user_id","scene","risk_level","summary_cipher","create_time","update_time","deleted"),
    "user",java.util.List.of("baseline_nudge_enabled","baseline_nudge_last_at","baseline_nudge_last_level"),
    "admin_export_log",java.util.List.of("id","actor_id","target_type","field_list","batch_no","create_time"),
    "heritage_catalog_version",java.util.List.of("id","version_no","payload","note","actor_id","create_time","update_time","deleted")).entrySet()){
   var rows=mock(ResultSet.class);var position=new java.util.concurrent.atomic.AtomicInteger(-1);
   when(rows.next()).thenAnswer(i->position.incrementAndGet()<entry.getValue().size());
   when(rows.getString("COLUMN_NAME")).thenAnswer(i->entry.getValue().get(position.get()));
   when(metadata.getColumns("zhiyu",null,entry.getKey(),null)).thenReturn(rows);
  }
  // emotion_session.summary 类型检查（20261031_emotion_summary_text.sql 之后应为 TEXT）
  var emotionSession=mock(ResultSet.class);var esPos=new java.util.concurrent.atomic.AtomicInteger(-1);
  var esCols=java.util.List.of("id","summary");
  when(emotionSession.next()).thenAnswer(i->esPos.incrementAndGet()<esCols.size());
  when(emotionSession.getString("COLUMN_NAME")).thenAnswer(i->esCols.get(esPos.get()));
  when(emotionSession.getString("TYPE_NAME")).thenAnswer(i->"TEXT");
  when(metadata.getColumns("zhiyu",null,"emotion_session",null)).thenReturn(emotionSession);
  return source;
 }
}
