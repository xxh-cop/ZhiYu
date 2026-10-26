package com.zhiyu.modules.growth;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.BusinessAccess;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CourseLessonService {
    private final JdbcTemplate db;
    private final BusinessAccess access;
    public record Lesson(@NotBlank @Size(max=100) String title,@NotBlank @Size(max=100) String chapter,
        @NotNull @Min(1) @Max(999) Integer position,@NotBlank @Size(max=10000) String content,
        Long projectId,@NotNull Boolean published) {}
    private Map<String,Object> course(long id,boolean lock) {
        var rows=db.queryForList("SELECT * FROM learning_program WHERE id=? AND kind='COURSE' AND deleted=0"+(lock?" FOR UPDATE":""),id);
        if(rows.isEmpty())throw new BusinessException(1004,"课程不存在");return rows.get(0);
    }
    private boolean host(Map<String,Object> course,long uid) {
        int role=db.queryForObject("SELECT role FROM user WHERE id=?",Integer.class,uid);
        return role==3||((role==1||role==2)&&(((Number)course.get("owner_id")).longValue()==uid || db.queryForObject("SELECT COUNT(*) FROM program_collaborator WHERE program_id=? AND user_id=? AND deleted=0",Integer.class,course.get("id"),uid)>0));
    }
    private void learner(Map<String,Object> course,long uid) {
        if(!"PUBLISHED".equals(course.get("status")))throw new BusinessException(3001,"课程未开放或已取消");
        if(db.queryForObject("SELECT COUNT(*) FROM program_booking WHERE program_id=? AND user_id=? AND status IN ('BOOKED','CHECKED_IN') AND deleted=0",Integer.class,course.get("id"),uid)==0)throw new BusinessException(1003,"请先报名课程");
    }
    public List<Map<String,Object>> list(long id) {
        long uid=access.user();var course=course(id,false);boolean host=host(course,uid);if(!host)learner(course,uid);
        var rows=db.queryForList("SELECT l.*,COALESCE(p.completed,0) completed FROM course_lesson l LEFT JOIN lesson_progress p ON p.lesson_id=l.id AND p.user_id=? AND p.deleted=0 WHERE l.program_id=? AND l.deleted=0"+(host?"":" AND l.published=1")+" ORDER BY l.position,l.id",uid,id);
        for(var row:rows){row.put("id",row.get("id").toString());row.put("program_id",row.get("program_id").toString());if(row.get("project_id")!=null)row.put("project_id",row.get("project_id").toString());}
        return rows;
    }
    @Transactional public String save(long id,Long lessonId,Lesson r) {
        long uid=access.user();var c=course(id,true);if(!host(c,uid))throw new BusinessException(1003,"仅主办人或管理员可编辑课程");
        if("CANCELLED".equals(c.get("status")))throw new BusinessException(3001,"已取消课程不可编辑");
        if(r.projectId()!=null&&db.queryForObject("SELECT COUNT(*) FROM craft_project WHERE id=? AND status=1 AND deleted=0",Integer.class,r.projectId())==0)throw new BusinessException(1001,"请选择已上架的手作项目");
        if(db.queryForObject("SELECT COUNT(*) FROM course_lesson WHERE program_id=? AND position=? AND deleted=0 AND id<>?",Integer.class,id,r.position(),lessonId==null?-1L:lessonId)>0)throw new BusinessException(3001,"课节序号已被使用");
        if(lessonId==null){lessonId=IdWorker.getId();db.update("INSERT INTO course_lesson(id,program_id,chapter,title,position,content,project_id,published,create_time,update_time,deleted) VALUES(?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",lessonId,id,r.chapter().trim(),r.title().trim(),r.position(),r.content(),r.projectId(),r.published()?1:0);}
        else if(db.update("UPDATE course_lesson SET chapter=?,title=?,position=?,content=?,project_id=?,published=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND program_id=? AND deleted=0",r.chapter().trim(),r.title().trim(),r.position(),r.content(),r.projectId(),r.published()?1:0,lessonId,id)!=1)throw new BusinessException(1004,"课节不存在");
        return lessonId.toString();
    }
    @Transactional public void delete(long id,long lessonId){long uid=access.user();if(!host(course(id,true),uid))throw new BusinessException(1003,"仅主办人或管理员可编辑课程");if(db.update("UPDATE course_lesson SET deleted=1,update_time=CURRENT_TIMESTAMP WHERE program_id=? AND id=? AND deleted=0",id,lessonId)!=1)throw new BusinessException(1004,"课节不存在");}
    @Transactional public void progress(long id,long lessonId,boolean completed){
        long uid=access.consentedUser();db.queryForObject("SELECT id FROM user WHERE id=? FOR UPDATE",Long.class,uid);learner(course(id,true),uid);
        if(db.queryForObject("SELECT COUNT(*) FROM course_lesson WHERE id=? AND program_id=? AND published=1 AND deleted=0",Integer.class,lessonId,id)==0)throw new BusinessException(1004,"课节不存在或未发布");
        if(db.update("UPDATE lesson_progress SET completed=?,deleted=0,update_time=CURRENT_TIMESTAMP WHERE lesson_id=? AND user_id=?",completed?1:0,lessonId,uid)==0)db.update("INSERT INTO lesson_progress(id,lesson_id,user_id,completed,create_time,update_time,deleted) VALUES(?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",IdWorker.getId(),lessonId,uid,completed?1:0);
    }
}
