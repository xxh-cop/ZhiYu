package com.zhiyu.modules.growth;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.BusinessAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class ProgramCollaborationService {
    private final JdbcTemplate db;
    private final BusinessAccess access;
    public List<Map<String,Object>> hosts(){access.role(1,2,3);return db.queryForList("SELECT id,nickname,role FROM user WHERE role IN (1,2) AND status=1 AND deleted=0 ORDER BY nickname,id LIMIT 500").stream().map(r->{r.put("id",r.get("id").toString());return r;}).toList();}
    private Map<String,Object> program(long id){var rows=db.queryForList("SELECT * FROM learning_program WHERE id=? AND deleted=0",id);if(rows.isEmpty())throw new BusinessException(1004,"活动不存在");return rows.get(0);}
    public List<Map<String,Object>> list(long id){long uid=access.role(1,2,3);var p=program(id);int role=db.queryForObject("SELECT role FROM user WHERE id=?",Integer.class,uid);if(role!=3&&((Number)p.get("owner_id")).longValue()!=uid&&db.queryForObject("SELECT COUNT(*) FROM program_collaborator WHERE program_id=? AND user_id=? AND deleted=0",Integer.class,id,uid)==0)throw new BusinessException(1003,"无权查看带领团队");return db.queryForList("SELECT u.id,u.nickname,u.role FROM program_collaborator c JOIN user u ON u.id=c.user_id AND u.status=1 AND u.deleted=0 WHERE c.program_id=? AND c.deleted=0 ORDER BY u.id",id).stream().map(r->{r.put("id",r.get("id").toString());return r;}).toList();}
    @Transactional public void save(long id,List<Long> ids){long uid=access.role(1,2,3);var rows=db.queryForList("SELECT * FROM learning_program WHERE id=? AND deleted=0 FOR UPDATE",id);if(rows.isEmpty())throw new BusinessException(1004,"活动不存在");var p=rows.get(0);long owner=((Number)p.get("owner_id")).longValue();if(owner!=uid&&db.queryForObject("SELECT role FROM user WHERE id=?",Integer.class,uid)!=3)throw new BusinessException(1003,"只有主办人可以分配协作权限");if("GUIDANCE".equals(p.get("kind")))throw new BusinessException(3001,"一对一预约不开放协作权限");if(ids==null||ids.size()>5||new HashSet<>(ids).size()!=ids.size())throw new BusinessException(1001,"最多选择五名不同的协作带领者");for(Long target:ids)if(target==null||target==owner||db.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role IN (1,2) AND status=1 AND deleted=0",Integer.class,target)!=1)throw new BusinessException(1001,"请选择有效的带领者");db.update("UPDATE program_collaborator SET deleted=1,update_time=CURRENT_TIMESTAMP WHERE program_id=?",id);for(long target:ids)if(db.update("UPDATE program_collaborator SET deleted=0,update_time=CURRENT_TIMESTAMP WHERE program_id=? AND user_id=?",id,target)==0)db.update("INSERT INTO program_collaborator(id,program_id,user_id) VALUES(?,?,?)",IdWorker.getId(),id,target);}
}
