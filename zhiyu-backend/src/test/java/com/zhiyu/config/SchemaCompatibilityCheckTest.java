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
  assertTrue(error.getMessage().contains("20261012-runtime-columns.sql"));
 }
 @Test void upgradedSchemaCanStart() throws Exception {
  assertDoesNotThrow(()->new SchemaCompatibilityCheck(source(true)).run(null));
 }
 private DataSource source(boolean upgraded) throws Exception {
  var source=mock(DataSource.class);var connection=mock(Connection.class);var metadata=mock(DatabaseMetaData.class);
  when(source.getConnection()).thenReturn(connection);when(connection.getCatalog()).thenReturn("zhiyu");when(connection.getMetaData()).thenReturn(metadata);
  var work=mock(ResultSet.class);when(work.next()).thenReturn(true,upgraded,false);when(work.getString("COLUMN_NAME")).thenReturn(upgraded?"media_type":"id","video_url");
  var session=mock(ResultSet.class);when(session.next()).thenReturn(true,false);when(session.getString("COLUMN_NAME")).thenReturn("emotion_session_id");
  var project=mock(ResultSet.class);when(project.next()).thenReturn(true,false);when(project.getString("COLUMN_NAME")).thenReturn("video_url");var media=mock(ResultSet.class);when(media.next()).thenReturn(true,true,false);when(media.getString("COLUMN_NAME")).thenReturn("storage_key","user_id");when(metadata.getColumns("zhiyu",null,"craft_project",null)).thenReturn(project);when(metadata.getColumns("zhiyu",null,"media_asset",null)).thenReturn(media);
  when(metadata.getColumns("zhiyu",null,"work",null)).thenReturn(work);when(metadata.getColumns("zhiyu",null,"craft_session",null)).thenReturn(session);return source;
 }
}
