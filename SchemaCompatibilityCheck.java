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
   var required=Map.of("work",List.of("media_type","video_url"),"craft_session",List.of("emotion_session_id"));var missing=new ArrayList<String>();
   for(var entry:required.entrySet()){var actual=new HashSet<String>();try(var rows=connection.getMetaData().getColumns(connection.getCatalog(),null,entry.getKey(),null)){while(rows.next())actual.add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));}for(var field:entry.getValue())if(!actual.contains(field))missing.add(entry.getKey()+"."+field);}
   if(!missing.isEmpty())throw new IllegalStateException("数据库结构尚未升级，缺少字段："+String.join(", ",missing)+"。请执行 sql/migrations/20261012-runtime-columns.sql 后重新启动。仅执行 CREATE TABLE IF NOT EXISTS 不会升级已有表。");
  }
 }
}
