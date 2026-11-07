package com.zhiyu.modules.work.service;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.BusinessAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class PersonalWorkService {
    private final com.zhiyu.modules.file.MediaService media;
    private final JdbcTemplate db;
    private final BusinessAccess access;
    /** 软档处置链读侧开关（2026-11-07）：关闭时「我的作品」不下发 softDemoteUntil（与排序忽略同口径）。 */
    @org.springframework.beans.factory.annotation.Value("${zhiyu.moderation.soft-tier.disposition.enabled:false}")
    private boolean softDispositionEnabled;
    @Transactional public void unfavorite(long id){
        long uid=access.user();
        db.update("UPDATE work_like SET deleted=1,update_time=CURRENT_TIMESTAMP WHERE user_id=? AND work_id=? AND type=0 AND deleted=0",uid,id);
    }
    public Map<String,Object> favorites(int page){
        long uid=access.user();
        if(page<1||page>100000)throw new BusinessException(1001,"分页无效");
        String from=" FROM work_like l JOIN work w ON w.id=l.work_id WHERE l.user_id=? AND l.type=0 AND l.deleted=0 AND w.deleted=0 AND w.status=1 AND (w.visible=1 OR w.user_id=? OR (w.visible=2 AND EXISTS(SELECT 1 FROM group_work g JOIN group_member m ON m.group_id=g.group_id AND m.deleted=0 JOIN support_group s ON s.id=g.group_id AND s.status=1 AND s.deleted=0 WHERE g.work_id=w.id AND g.deleted=0 AND m.user_id=?)))";
        Long total=db.queryForObject("SELECT COUNT(*)"+from,Long.class,uid,uid,uid);
        var records=db.queryForList("SELECT TRIM(CAST(w.id AS CHAR(20))) id,w.title,w.cover_url coverUrl,w.media_type mediaType,w.message,w.visible,w.create_time createTime,l.update_time savedAt"+from+" ORDER BY l.update_time DESC,l.id DESC LIMIT 12 OFFSET ?",uid,uid,uid,(page-1)*12);
        for(var r:records)r.put("coverUrl",media.imageUrl(uid,(String)r.get("coverUrl")));
        // S3（2026-11-04）：分页元数据与列表内嵌计数统一为 JSON number
        return Map.of("records",records,"total",total==null?0:total.intValue());
    }
    public Map<String,Object> mine(int page,Integer status){
        long uid=access.user();
        if(page<1||page>100000||(status!=null&&(status<0||status>2)))throw new BusinessException(1001,"分页或状态无效");
        String where=" WHERE user_id=? AND deleted=0"+(status==null?"":" AND status=?");
        List<Object> args=new ArrayList<>();args.add(uid);if(status!=null)args.add(status);
        Long total=db.queryForObject("SELECT COUNT(*) FROM work"+where,Long.class,args.toArray());
        args.add((page-1)*12);
        var records=db.queryForList("SELECT TRIM(CAST(id AS CHAR(20))) id,title,cover_url coverUrl,media_type mediaType,video_url videoUrl,message,visible,status,anonymous,like_count likeCount,soft_demote_until softDemoteUntil,create_time createTime FROM work"+where+" ORDER BY create_time DESC,id DESC LIMIT 12 OFFSET ?",args.toArray());
        Map<String,Integer> counts=new LinkedHashMap<>();counts.put("all",0);for(var row:db.queryForList("SELECT status,COUNT(*) amount FROM work WHERE user_id=? AND deleted=0 GROUP BY status",uid)){int count=((Number)row.get("amount")).intValue();counts.put(row.get("status").toString(),count);counts.put("all",counts.get("all")+count);}
        for(var r:records){
            r.put("coverUrl",media.imageUrl(uid,(String)r.get("coverUrl")));
            // softDemoteUntil 三条件口径（开关开启+本人[本接口恒真]+未过期）：不满足一律置 null 不下发
            Object demote=r.get("softDemoteUntil");
            // 时间列统一走 JdbcTimes（MySQL 返回 LocalDateTime、H2 返回 Timestamp，裸强转在 MySQL 下 CCE——联调 F1-a）
            if(!softDispositionEnabled||demote==null||!com.zhiyu.common.util.JdbcTimes.toLocalDateTime(demote).isAfter(java.time.LocalDateTime.now()))r.put("softDemoteUntil",null);
        }
        return Map.of("records",records,"total",total==null?0:total.intValue(),"counts",counts);
    }
    @Transactional public void withdraw(long id){
        long uid=access.user();
        var rows=db.queryForList("SELECT user_id FROM work WHERE id=? AND deleted=0 FOR UPDATE",id);
        if(rows.isEmpty())throw new BusinessException(1004,"作品已不存在");
        if(((Number)rows.get(0).get("user_id")).longValue()!=uid)throw new BusinessException(1003,"只能撤回自己的作品");
        db.update("UPDATE work SET deleted=1,update_time=CURRENT_TIMESTAMP WHERE id=?",id);
        db.update("UPDATE work_comment SET content='',deleted=1,update_time=CURRENT_TIMESTAMP WHERE work_id=? AND deleted=0",id);
        db.update("UPDATE work_like SET deleted=1,update_time=CURRENT_TIMESTAMP WHERE work_id=? AND deleted=0",id);
    }
}
