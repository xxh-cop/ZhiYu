package com.zhiyu.modules.emotion.controller;
import com.zhiyu.common.result.Result;
import com.zhiyu.security.BusinessAccess;
import com.zhiyu.modules.emotion.service.EmotionService;
import com.zhiyu.modules.campus.CampusService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/emotion/sessions")
public class EmotionHistoryController {
    private final JdbcTemplate jdbc;
    private final BusinessAccess access;
    private final EmotionService service;
    public record State(@jakarta.validation.constraints.Pattern(regexp="RESUME|END") @jakarta.validation.constraints.NotBlank String action){}
    @PutMapping("/{id}/state") @Transactional public Result<?> state(@PathVariable Long id,@jakarta.validation.Valid @RequestBody State input){long uid=access.consentedUser();access.emotionSession(id,uid);jdbc.update("UPDATE emotion_session SET status=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND deleted=0",input.action().equals("END")?1:0,id,uid);return Result.success(service.getSession(id));}
    @GetMapping public Result<?> list(@RequestParam(defaultValue="1") int page){Long uid=access.consentedUser();return Result.success(Map.of("records",jdbc.queryForList("SELECT id FROM emotion_session WHERE user_id=? AND deleted=0 ORDER BY create_time DESC,id DESC LIMIT 20 OFFSET ?",uid,CampusService.offset(page)).stream().map(r->service.getSession(((Number)r.get("id")).longValue())).toList(),"total",jdbc.queryForObject("SELECT COUNT(*) FROM emotion_session WHERE user_id=? AND deleted=0",Long.class,uid)));}
    @GetMapping("/{id}") public Result<?> detail(@PathVariable Long id){access.emotionSession(id,access.consentedUser());return Result.success(Map.of("session",service.getSession(id),"messages",service.loadRecentHistory(id,100)));}
    @DeleteMapping("/{id}") @Transactional public Result<?> delete(@PathVariable Long id){access.emotionSession(id,access.user());jdbc.update("DELETE FROM emotion_message WHERE session_id=?",id);jdbc.update("UPDATE emotion_session SET summary=NULL,mood=NULL,deleted=1,update_time=CURRENT_TIMESTAMP WHERE id=?",id);return Result.success();}
}
