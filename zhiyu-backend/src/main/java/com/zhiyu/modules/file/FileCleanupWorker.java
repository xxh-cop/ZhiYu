package com.zhiyu.modules.file;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
@Configuration @EnableScheduling @RequiredArgsConstructor
public class FileCleanupWorker {
    private final JdbcTemplate db;private final MediaStorage storage;
    @org.springframework.beans.factory.annotation.Value("${zhiyu.file.upload-dir:./uploads}") private String uploadDirectory;
    @Scheduled(fixedDelay=60000,initialDelay=60000) public void retry(){
        for(var row:db.queryForList("SELECT id,storage_kind,storage_key FROM file_cleanup_job WHERE state='PENDING' AND deleted=0 ORDER BY update_time,id LIMIT 50")){
            try{String key=row.get("storage_key").toString();if("PUBLIC".equals(row.get("storage_kind"))){
                if(!key.matches("/uploads/images/[0-9]{4}/[0-9]{2}/[0-9]{2}/[a-f0-9]{32}\\.(jpg|jpeg|png|webp)"))throw new IllegalArgumentException("Invalid public cleanup key");
                long referenced=db.queryForObject("SELECT (SELECT COUNT(*) FROM work WHERE cover_url=? AND deleted=0)+(SELECT COUNT(*) FROM craft_project WHERE cover_url=? AND deleted=0)+(SELECT COUNT(*) FROM user WHERE avatar_url=? AND deleted=0)",Long.class,key,key,key);
                if(referenced>0){db.update("UPDATE file_cleanup_job SET update_time=CURRENT_TIMESTAMP WHERE id=?",row.get("id"));continue;}
                var root=java.nio.file.Path.of(uploadDirectory).toAbsolutePath().normalize();var path=root.resolve(key.substring("/uploads/".length())).normalize();if(!path.startsWith(root))throw new IllegalArgumentException("Invalid cleanup target");java.nio.file.Files.deleteIfExists(path);
            }else if("PRIVATE".equals(row.get("storage_kind")))storage.remove(key);else throw new IllegalArgumentException("Unknown storage kind");db.update("UPDATE file_cleanup_job SET state='DONE',attempts=attempts+1,update_time=CURRENT_TIMESTAMP WHERE id=?",row.get("id"));}
            catch(Exception e){db.update("UPDATE file_cleanup_job SET attempts=attempts+1,update_time=CURRENT_TIMESTAMP WHERE id=?",row.get("id"));}
        }
    }
}
