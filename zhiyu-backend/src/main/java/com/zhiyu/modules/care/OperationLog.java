package com.zhiyu.modules.care;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhiyu.security.BusinessAccess;
import com.zhiyu.security.UserContext;
import com.zhiyu.modules.campus.CampusService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;
@Service
@RequiredArgsConstructor
public class OperationLog {
    private final JdbcTemplate jdbc;
    private final BusinessAccess access;
    public void record(String action,Long target){jdbc.update("INSERT INTO admin_operation_log(id,actor_id,action,target_id,create_time,update_time,deleted) VALUES(?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",IdWorker.getId(),UserContext.getUserId(),action,target);}
    public Map<String,Object> list(int page){access.role(3);return Map.of("records",jdbc.queryForList("SELECT TRIM(CAST(id AS CHAR(20))) id,TRIM(CAST(actor_id AS CHAR(20))) actor,action,TRIM(CAST(target_id AS CHAR(20))) target,create_time FROM admin_operation_log ORDER BY create_time DESC,id DESC LIMIT 20 OFFSET ?",CampusService.offset(page)),"total",jdbc.queryForObject("SELECT COUNT(*) FROM admin_operation_log",Long.class));}
}
