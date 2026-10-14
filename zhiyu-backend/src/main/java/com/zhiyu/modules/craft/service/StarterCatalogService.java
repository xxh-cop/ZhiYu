package com.zhiyu.modules.craft.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.UserContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Original editorial guides; publication and session ownership remain database-controlled. */
@Service
public class StarterCatalogService {
    private final JdbcTemplate db;
    private final List<Map<String,Object>> catalog;
    public StarterCatalogService(JdbcTemplate db, ObjectMapper json) throws java.io.IOException {
        this.db=db;
        try(var reader=new InputStreamReader(new ClassPathResource("catalog/starter-crafts.json").getInputStream(),StandardCharsets.UTF_8)) {
            catalog=json.readValue(reader,new TypeReference<>(){});
        }
    }
    private long user() {
        Long id=UserContext.getUserId();
        if(id==null)throw new BusinessException(1002,"请先登录");
        if(db.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND status=1 AND deleted=0",Integer.class,id)!=1)
            throw new BusinessException(1003,"账号不可用");
        return id;
    }
    public List<Map<String,Object>> available() {
        user();var result=new ArrayList<Map<String,Object>>();
        for(var guide:catalog) {
            var rows=db.queryForList("SELECT title,est_minutes FROM craft_project WHERE id=? AND status=1 AND deleted=0",Long.parseLong(guide.get("id").toString()));
            if(rows.isEmpty())continue;
            // Hide stale guides after an editor changes steps/materials: don't show contradictory preparation.
            var steps=db.queryForList("SELECT title,guide FROM craft_step WHERE project_id=? AND deleted=0 ORDER BY step_no",Long.parseLong(guide.get("id").toString()));
            var expected=(List<?>)guide.get("steps");
            if(steps.size()!=expected.size())continue;
            boolean consistent=true;
            for(int i=0;i<steps.size();i++) {
                var e=(Map<?,?>)expected.get(i);
                if(!Objects.equals(steps.get(i).get("title"),e.get("title")) || !Objects.equals(steps.get(i).get("guide"),e.get("guide")+"\n遇到困难："+e.get("rescue")))consistent=false;
            }
            var materials=db.queryForList("SELECT name,qty FROM craft_material WHERE project_id=? AND deleted=0",Long.parseLong(guide.get("id").toString()));
            var expectedMaterials=(List<?>)guide.get("materials");
            if(materials.size()!=expectedMaterials.size())consistent=false;
            for(var m:expectedMaterials) {
                var e=(Map<?,?>)m;
                if(materials.stream().noneMatch(x->Objects.equals(x.get("name"),e.get("name"))&&Objects.equals(x.get("qty"),e.get("qty"))))consistent=false;
            }
            if(!consistent)continue;
            var item=new LinkedHashMap<>(guide);item.put("title",rows.get(0).get("title"));
            var minutes=rows.get(0).get("est_minutes");if(minutes!=null)item.put("minutes",minutes);
            result.add(item);
        }
        return result;
    }
    public List<Map<String,Object>> active(Long projectId) {
        long uid=user();
        var args=new ArrayList<Object>();args.add(uid);if(projectId!=null)args.add(projectId);
        return db.query("SELECT s.id,s.project_id,p.title,s.current_step,s.status,(SELECT COUNT(*) FROM craft_step st WHERE st.project_id=p.id AND st.deleted=0) total_steps FROM craft_session s JOIN craft_project p ON p.id=s.project_id AND p.status=1 AND p.deleted=0 WHERE s.user_id=? AND s.status IN (1,2) AND s.deleted=0"+(projectId==null?"":" AND s.project_id=?")+" ORDER BY s.update_time DESC,s.id DESC LIMIT 20",
            (rs,n)->Map.<String,Object>of("sessionId",rs.getString("id"),"projectId",rs.getString("project_id"),"projectTitle",rs.getString("title"),"currentStep",rs.getInt("current_step"),"status",rs.getInt("status"),"totalSteps",rs.getInt("total_steps")),args.toArray());
    }
    public Map<String,Object> quality() {
        long uid=user();
        if(db.queryForObject("SELECT role FROM user WHERE id=?",Integer.class,uid)!=3)throw new BusinessException(1003,"仅管理员可查看过程汇总");
        long started=0,finished=0,paused=0,abandoned=0;
        for(var item:catalog) {
            var rows=db.queryForList("SELECT status,COUNT(*) total FROM craft_session WHERE project_id=? AND deleted=0 GROUP BY status",Long.parseLong(item.get("id").toString()));
            for(var row:rows){int state=((Number)row.get("status")).intValue();long n=((Number)row.get("total")).longValue();if(state>=1&&state<=4)started+=n;if(state==3)finished+=n;if(state==2)paused+=n;if(state==4)abandoned+=n;}
        }
        var result=new LinkedHashMap<String,Object>();result.put("started",started);result.put("finished",finished);result.put("paused",paused);result.put("abandoned",abandoned);result.put("completionRate",started==0?null:Math.round(1000.0*finished/started)/10.0);return result;
    }
}
