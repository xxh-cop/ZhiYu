package com.zhiyu.modules.emotion.controller;

import com.zhiyu.common.dto.PageVO;
import com.zhiyu.common.result.Result;
import com.zhiyu.modules.emotion.dto.NoteCreateDTO;
import com.zhiyu.modules.emotion.dto.NoteUpdateDTO;
import com.zhiyu.modules.emotion.service.EmotionNoteService;
import com.zhiyu.modules.emotion.vo.NoteDetailVO;
import com.zhiyu.modules.emotion.vo.NoteListItemVO;
import com.zhiyu.security.BusinessAccess;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 心情便签控制器（M3-A1）。
 *
 * <p>提供 5 个 RESTful 接口，路径前缀 {@code /api/emotion/notes}：
 * <ul>
 *   <li>POST   /api/emotion/notes          — 创建便签</li>
 *   <li>GET    /api/emotion/notes          — 分页查询本人便签</li>
 *   <li>GET    /api/emotion/notes/{id}     — 查询便签详情</li>
 *   <li>PUT    /api/emotion/notes/{id}     — 编辑便签</li>
 *   <li>DELETE /api/emotion/notes/{id}     — 删除便签（逻辑删除）</li>
 * </ul>
 *
 * <p>所有接口默认 authenticated（SecurityConfig 无需额外配置）。
 * content 加密存储，仅本人可见（AGENTS.md §5 / FR-10-03）。
 *
 * <p>隐私同意门（S1 2026-11-04）：便签属心理敏感数据，写入与读取均需已同意隐私政策
 * （{@code consentedUser()}，未同意 1003）；删除属撤回权利，与情绪会话删除一致
 * 只做账号状态复核（{@code user()}），不因未同意而阻断撤回。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/emotion/notes")
public class EmotionNoteController {

    private final EmotionNoteService noteService;
    private final BusinessAccess access;

    /** 创建便签 → 返回 { id } */
    @PostMapping
    public Result<Map<String, Long>> createNote(@Valid @RequestBody NoteCreateDTO dto) {
        Long userId = access.consentedUser();
        Long id = noteService.createNote(userId, dto);
        return Result.success(Map.of("id", id));
    }

    /** 分页查询本人便签 */
    @GetMapping
    public Result<PageVO<NoteListItemVO>> listNotes(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long userId = access.consentedUser();
        PageVO<NoteListItemVO> vo = noteService.listNotes(userId, page, pageSize, startDate, endDate);
        return Result.success(vo);
    }

    /** 查询便签详情 */
    @GetMapping("/{id}")
    public Result<NoteDetailVO> getNoteDetail(@PathVariable Long id) {
        Long userId = access.consentedUser();
        NoteDetailVO vo = noteService.getNoteDetail(userId, id);
        return Result.success(vo);
    }

    /** 编辑便签（仅更新非 null 字段） */
    @PutMapping("/{id}")
    public Result<Void> updateNote(@PathVariable Long id, @RequestBody NoteUpdateDTO dto) {
        Long userId = access.consentedUser();
        noteService.updateNote(userId, id, dto);
        return Result.success();
    }

    /** 删除便签（逻辑删除；撤回类操作只做账号状态复核） */
    @DeleteMapping("/{id}")
    public Result<Void> deleteNote(@PathVariable Long id) {
        Long userId = access.user();
        noteService.deleteNote(userId, id);
        return Result.success();
    }
}
