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
  var work=mock(ResultSet.class);when(work.next()).thenReturn(true,upgraded,false);when(work.getString("COLUMN_NAME")).thenReturn(upgraded?"media_type":"id","video_url");
  var session=mock(ResultSet.class);when(session.next()).thenReturn(true,false);when(session.getString("COLUMN_NAME")).thenReturn("emotion_session_id");
  var project=mock(ResultSet.class);when(project.next()).thenReturn(true,false);when(project.getString("COLUMN_NAME")).thenReturn("video_url");var media=mock(ResultSet.class);when(media.next()).thenReturn(true,true,false);when(media.getString("COLUMN_NAME")).thenReturn("storage_key","user_id");when(metadata.getColumns("zhiyu",null,"craft_project",null)).thenReturn(project);when(metadata.getColumns("zhiyu",null,"media_asset",null)).thenReturn(media);
  when(metadata.getColumns("zhiyu",null,"work",null)).thenReturn(work);when(metadata.getColumns("zhiyu",null,"craft_session",null)).thenReturn(session);return source;
 }
}
