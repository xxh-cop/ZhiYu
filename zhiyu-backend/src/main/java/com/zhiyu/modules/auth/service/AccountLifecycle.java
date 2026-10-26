package com.zhiyu.modules.auth.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Invoked under the target user's row lock. Never alters append-only AI audit records. */
@Service @RequiredArgsConstructor
public class AccountLifecycle {
    private final JdbcTemplate db;
    @Transactional public void suspend(long uid){
        db.queryForObject("SELECT id FROM user WHERE id=? FOR UPDATE",Long.class,uid);
        var groups=db.queryForList("SELECT group_id FROM group_member WHERE user_id=? AND deleted=0 ORDER BY group_id",Long.class,uid);
        for(long group:groups){db.queryForObject("SELECT id FROM support_group WHERE id=? FOR UPDATE",Long.class,group);boolean leader=db.queryForObject("SELECT COUNT(*) FROM group_member WHERE group_id=? AND user_id=? AND role='组长' AND deleted=0",Integer.class,group,uid)>0;
            db.update("UPDATE group_member SET deleted=1,role='成员',update_time=CURRENT_TIMESTAMP WHERE group_id=? AND user_id=?",group,uid);
            if(leader){var next=db.queryForList("SELECT m.id FROM group_member m JOIN user u ON u.id=m.user_id AND u.status=1 AND u.deleted=0 WHERE m.group_id=? AND m.deleted=0 ORDER BY m.create_time,m.id LIMIT 1",Long.class,group);if(!next.isEmpty())db.update("UPDATE group_member SET role='组长',update_time=CURRENT_TIMESTAMP WHERE id=?",next.get(0));else db.update("UPDATE support_group SET status=0,update_time=CURRENT_TIMESTAMP WHERE id=?",group);}
            db.update("UPDATE support_group SET member_count=(SELECT COUNT(*) FROM group_member WHERE group_id=? AND deleted=0),update_time=CURRENT_TIMESTAMP WHERE id=?",group,group);
        }
        db.update("UPDATE group_companion SET state='CANCELLED',update_time=CURRENT_TIMESTAMP WHERE first_user_id=? OR second_user_id=?",uid,uid);
        db.update("UPDATE program_collaborator SET deleted=1,update_time=CURRENT_TIMESTAMP WHERE user_id=?",uid);
        db.update("UPDATE learning_program SET status='CANCELLED',update_time=CURRENT_TIMESTAMP WHERE owner_id=?",uid);
        db.update("UPDATE program_booking SET status='CANCELLED',update_time=CURRENT_TIMESTAMP WHERE user_id=? AND status IN ('PENDING','BOOKED')",uid);
        db.update("UPDATE guidance_channel SET enabled=0,update_time=CURRENT_TIMESTAMP WHERE booking_id IN (SELECT id FROM program_booking WHERE user_id=?)",uid);
        db.update("UPDATE care_case SET owner_id=NULL,update_time=CURRENT_TIMESTAMP WHERE owner_id=?",uid);
    }
    @Transactional public void erase(long uid){
        suspend(uid);
        var images=new HashSet<String>();
        images.addAll(db.queryForList("SELECT cover_url FROM work WHERE user_id=? AND cover_url IS NOT NULL",String.class,uid));
        images.addAll(db.queryForList("SELECT avatar_url FROM user WHERE id=? AND avatar_url IS NOT NULL",String.class,uid));
        images.addAll(db.queryForList("SELECT cover_url FROM craft_project WHERE inheritor_id=? AND cover_url IS NOT NULL",String.class,uid));
        for(String image:images)if(image.matches("/uploads/images/[0-9]{4}/[0-9]{2}/[0-9]{2}/[a-f0-9]{32}\\.(jpg|jpeg|png|webp)")&&db.queryForObject("SELECT COUNT(*) FROM file_cleanup_job WHERE storage_kind='PUBLIC' AND storage_key=?",Integer.class,image)==0)db.update("INSERT INTO file_cleanup_job(id,storage_kind,storage_key) VALUES(?,'PUBLIC',?)",IdWorker.getId(),image);
        for(var row:db.queryForList("SELECT storage_key FROM media_asset WHERE user_id=? AND deleted=0",uid)){String key=row.get("storage_key").toString();if(db.queryForObject("SELECT COUNT(*) FROM file_cleanup_job WHERE storage_kind='PRIVATE' AND storage_key=?",Integer.class,key)==0)db.update("INSERT INTO file_cleanup_job(id,storage_kind,storage_key) VALUES(?,'PRIVATE',?)",IdWorker.getId(),key);}
        db.update("UPDATE media_asset SET deleted=1,update_time=CURRENT_TIMESTAMP WHERE user_id=?",uid);
        db.update("UPDATE emotion_message SET content='',deleted=1,update_time=CURRENT_TIMESTAMP WHERE session_id IN (SELECT id FROM emotion_session WHERE user_id=?)",uid);
        db.update("UPDATE emotion_session SET summary=NULL,mood=NULL,deleted=1,update_time=CURRENT_TIMESTAMP WHERE user_id=?",uid);
        db.update("UPDATE emotion_note SET content='',deleted=1,update_time=CURRENT_TIMESTAMP WHERE user_id=?",uid);
        db.update("DELETE FROM wellbeing_record WHERE user_id=?",uid);
        db.update("UPDATE guidance_message SET content='',deleted=1,update_time=CURRENT_TIMESTAMP WHERE user_id=? OR booking_id IN (SELECT id FROM program_booking WHERE user_id=?)",uid,uid);
        db.update("UPDATE group_message SET content='',deleted=1,update_time=CURRENT_TIMESTAMP WHERE user_id=?",uid);
        db.update("UPDATE campus_verification SET identity_cipher='',review_reason=NULL,deleted=1,update_time=CURRENT_TIMESTAMP WHERE user_id=?",uid);
        db.update("UPDATE work_comment SET content='',deleted=1,update_time=CURRENT_TIMESTAMP WHERE user_id=? OR work_id IN (SELECT id FROM work WHERE user_id=?)",uid,uid);
        db.update("UPDATE work_like SET deleted=1,update_time=CURRENT_TIMESTAMP WHERE user_id=? OR work_id IN (SELECT id FROM work WHERE user_id=?)",uid,uid);
        db.update("UPDATE work SET title='已移除作品',message=NULL,cover_url=NULL,video_url=NULL,status=0,visible=0,deleted=1,update_time=CURRENT_TIMESTAMP WHERE user_id=?",uid);
        db.update("UPDATE care_followup SET deleted=1,update_time=CURRENT_TIMESTAMP WHERE reminder_id IN (SELECT id FROM care_reminder WHERE user_id=?)",uid);
        db.update("UPDATE care_case SET deleted=1,update_time=CURRENT_TIMESTAMP WHERE reminder_id IN (SELECT id FROM care_reminder WHERE user_id=?)",uid);
        db.update("UPDATE care_reminder SET summary='',deleted=1,update_time=CURRENT_TIMESTAMP WHERE user_id=?",uid);
        for(String table:List.of("craft_session","craft_recommendation","lesson_progress","program_booking","inheritor_profile"))db.update("UPDATE "+table+" SET deleted=1,update_time=CURRENT_TIMESTAMP WHERE user_id=?",uid);
        db.update("UPDATE craft_project SET status=0,cover_url=NULL,video_url=NULL,update_time=CURRENT_TIMESTAMP WHERE inheritor_id=?",uid);
        db.update("UPDATE user SET username=?,password='',nickname='已注销用户',avatar_url=NULL,school=NULL,intro=NULL,craft_preferences=NULL,can_contact=0,privacy_agreed=0,status=0,update_time=CURRENT_TIMESTAMP WHERE id=?","deleted_"+uid,uid);
    }
}
