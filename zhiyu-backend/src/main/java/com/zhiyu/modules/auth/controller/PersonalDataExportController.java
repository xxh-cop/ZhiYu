package com.zhiyu.modules.auth.controller;

import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.Result;
import com.zhiyu.security.BusinessAccess;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

/** Explicit field allowlists keep credentials and other people's data out of exports. */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class PersonalDataExportController {
    private final JdbcTemplate db;
    private final BusinessAccess access;
    private final AesEncryptor crypto;

    @GetMapping("/data-export")
    public Result<Map<String, Object>> export(@RequestParam String category,
            @RequestParam(defaultValue = "0") String after, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        long uid = access.user();
        long cursor;
        try { cursor = Long.parseLong(after); if (cursor < 0) throw new NumberFormatException(); }
        catch (NumberFormatException e) { throw new BusinessException(1001, "导出游标无效"); }
        String query = switch (category) {
            case "profile" -> "SELECT id,username,nickname,school,intro,craft_preferences,privacy_agreed,can_contact,create_time FROM user WHERE id=? AND deleted=0 AND id>?";
            case "notes" -> "SELECT id,content,mood,note_date,create_time,update_time FROM emotion_note WHERE user_id=? AND deleted=0 AND id>?";
            case "sessions" -> "SELECT id,mood,summary,status,create_time,update_time FROM emotion_session WHERE user_id=? AND deleted=0 AND id>?";
            case "messages" -> "SELECT m.id,m.session_id,m.role,m.content,m.create_time FROM emotion_message m JOIN emotion_session s ON s.id=m.session_id AND s.deleted=0 WHERE s.user_id=? AND m.deleted=0 AND m.id>?";
            case "records" -> "SELECT id,kind,record_key,payload,create_time,update_time FROM wellbeing_record WHERE user_id=? AND deleted=0 AND id>?";
            case "works" -> "SELECT id,project_id,session_id,title,media_type,message,visible,anonymous,status,create_time,update_time FROM work WHERE user_id=? AND deleted=0 AND id>?";
            case "bookings" -> "SELECT b.id,b.program_id,b.status,b.create_time,b.update_time,p.kind AS program_kind,p.title AS program_title,p.location,p.start_time,p.end_time,p.status AS program_status FROM program_booking b LEFT JOIN learning_program p ON p.id=b.program_id AND p.deleted=0 WHERE b.user_id=? AND b.deleted=0 AND b.id>?";
            case "campus" -> "SELECT id,school,college,identity_cipher,status,review_reason,create_time,update_time FROM campus_verification WHERE user_id=? AND deleted=0 AND id>?";
            case "crafts" -> "SELECT id,project_id,status,flow_seconds,create_time,update_time FROM craft_session WHERE user_id=? AND deleted=0 AND id>?";
            case "lessons" -> "SELECT id,lesson_id,completed,create_time,update_time FROM lesson_progress WHERE user_id=? AND deleted=0 AND id>?";
            case "memberships" -> "SELECT id,group_id,role,create_time,update_time FROM group_member WHERE user_id=? AND deleted=0 AND id>?";
            case "group-messages" -> "SELECT id,group_id,content,create_time,update_time FROM group_message WHERE user_id=? AND deleted=0 AND id>?";
            case "guidance-messages" -> "SELECT id,booking_id,content,create_time,update_time FROM guidance_message WHERE user_id=? AND deleted=0 AND id>?";
            case "comments" -> "SELECT id,work_id,content,status,create_time,update_time FROM work_comment WHERE user_id=? AND deleted=0 AND id>?";
            case "favorites" -> "SELECT id,work_id,type,create_time,update_time FROM work_like WHERE user_id=? AND deleted=0 AND id>?";
            case "media" -> "SELECT id,size_bytes,create_time,update_time FROM media_asset WHERE user_id=? AND deleted=0 AND id>?";
            default -> throw new BusinessException(1001, "不支持的导出类别");
        };
        String orderColumn = switch (category) { case "messages" -> "m.id"; case "bookings" -> "b.id"; default -> "id"; };
        var rows = db.queryForList(query + " ORDER BY " + orderColumn + " ASC LIMIT 101", uid, cursor);
        boolean hasMore = rows.size() > 100;
        if (hasMore) rows = new ArrayList<>(rows.subList(0, 100));
        for (var row : rows) {
            // H2 returns CLOBs where MySQL returns Strings; exports must contain plain text.
            for (var entry : row.entrySet()) {
                if (entry.getValue() instanceof java.sql.Clob text) {
                    try { entry.setValue(text.getSubString(1, Math.toIntExact(text.length()))); }
                    catch (Exception e) { throw new BusinessException(5000, "部分记录暂时无法读取，本批未导出，请稍后重试"); }
                }
            }
            for (String key : List.of("content", "summary", "payload", "identity_cipher")) {
                Object cipher = row.get(key);
                if (!category.equals("comments") && cipher != null && !cipher.toString().isBlank()) {
                    try { row.put(key.equals("identity_cipher") ? "identity" : key, crypto.decrypt(cipher.toString())); }
                    catch (Exception e) { throw new BusinessException(5000, "部分记录暂时无法读取，本批未导出，请稍后重试"); }
                }
            }
            row.remove("identity_cipher");
            for (String key : List.of("id", "session_id", "project_id", "program_id", "lesson_id", "group_id", "booking_id", "work_id")) {
                if (row.get(key) != null) row.put(key, row.get(key).toString());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", 1);
        result.put("category", category);
        result.put("exportedAt", Instant.now().toString());
        result.put("rows", rows);
        result.put("hasMore", hasMore);
        result.put("nextCursor", hasMore ? rows.get(rows.size() - 1).get("id") : null);
        return Result.success(result);
    }
}
