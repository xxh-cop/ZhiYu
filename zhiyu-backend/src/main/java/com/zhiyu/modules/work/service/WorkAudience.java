package com.zhiyu.modules.work.service;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.work.entity.Work;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class WorkAudience {
    private final JdbcTemplate db;
    public boolean canRead(Work w,Long uid){return w!=null&&Objects.equals(w.getStatus(),1)&&(Objects.equals(w.getVisible(),1)||Objects.equals(w.getUserId(),uid)||(Objects.equals(w.getVisible(),2)&&uid!=null&&db.queryForObject("SELECT COUNT(*) FROM group_work g JOIN group_member m ON m.group_id=g.group_id AND m.deleted=0 JOIN support_group s ON s.id=g.group_id AND s.deleted=0 AND s.status=1 WHERE g.work_id=? AND g.deleted=0 AND m.user_id=?",Integer.class,w.getId(),uid)>0));}
    public void member(Long group,Long uid){if(group==null||db.queryForList("SELECT s.id FROM support_group s JOIN group_member m ON m.group_id=s.id AND m.deleted=0 WHERE s.id=? AND s.status=1 AND s.deleted=0 AND m.user_id=? FOR UPDATE",group,uid).isEmpty())throw new BusinessException(1003,"请选择仍在参与的开放小组");}
    public void attach(long work,long group){db.update("INSERT INTO group_work(id,group_id,work_id) VALUES(?,?,?)",com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(),group,work);}
}
