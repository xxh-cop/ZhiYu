package com.zhiyu.modules.emotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.dto.PageVO;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.emotion.dto.NoteCreateDTO;
import com.zhiyu.modules.emotion.dto.NoteUpdateDTO;
import com.zhiyu.modules.emotion.entity.EmotionNote;
import com.zhiyu.modules.emotion.mapper.EmotionNoteMapper;
import com.zhiyu.modules.emotion.service.EmotionNoteService;
import com.zhiyu.modules.emotion.vo.NoteDetailVO;
import com.zhiyu.modules.emotion.vo.NoteListItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link EmotionNoteService} 实现（M3-A1）。
 *
 * <p>关键逻辑：
 * <ul>
 *   <li>createNote：content 用 {@link AesEncryptor#encrypt} 加密后存储</li>
 *   <li>listNotes / getNoteDetail：查询后用 {@link AesEncryptor#decrypt} 解密</li>
 *   <li>所有查询强制 {@code WHERE user_id = current}（仅本人可见）</li>
 *   <li>解密失败返回 {@code "[解密失败]"} 而非抛 500</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionNoteServiceImpl implements EmotionNoteService {

    private static final int EXCERPT_LENGTH = 30;
    private static final String DECRYPT_FAILED = "[解密失败]";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EmotionNoteMapper noteMapper;
    private final AesEncryptor encryptor;

    @Override
    public Long createNote(Long userId, NoteCreateDTO dto) {
        EmotionNote note = new EmotionNote();
        note.setUserId(userId);
        note.setMood(dto.getMood());
        note.setContent(encryptor.encrypt(dto.getContent()));
        note.setNoteDate(parseNoteDate(dto.getNoteDate()));
        noteMapper.insert(note);
        log.info("创建心情便签: noteId={}, userId={}", note.getId(), userId);
        return note.getId();
    }

    @Override
    public PageVO<NoteListItemVO> listNotes(Long userId, int page, int pageSize,
                                             String startDate, String endDate) {
        LambdaQueryWrapper<EmotionNote> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmotionNote::getUserId, userId)
               .orderByDesc(EmotionNote::getCreateTime)
               .orderByDesc(EmotionNote::getId);

        // 日期范围过滤
        if (startDate != null && !startDate.isBlank()) {
            LocalDate start = parseNoteDate(startDate);
            if (start != null) {
                wrapper.ge(EmotionNote::getNoteDate, start);
            }
        }
        if (endDate != null && !endDate.isBlank()) {
            LocalDate end = parseNoteDate(endDate);
            if (end != null) {
                wrapper.le(EmotionNote::getNoteDate, end);
            }
        }

        Page<EmotionNote> pageResult = noteMapper.selectPage(new Page<>(page, pageSize), wrapper);

        List<NoteListItemVO> records = pageResult.getRecords().stream()
                .map(this::toListVO)
                .collect(Collectors.toList());

        return new PageVO<>(pageResult.getTotal(), page, pageSize, records);
    }

    @Override
    public NoteDetailVO getNoteDetail(Long userId, Long noteId) {
        EmotionNote note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        checkOwnership(note, userId);
        String plainContent = decryptSafely(note.getContent());
        return new NoteDetailVO(
                note.getId(),
                note.getMood(),
                plainContent,
                note.getNoteDate(),
                note.getCreateTime(),
                note.getUpdateTime()
        );
    }

    @Override
    public void updateNote(Long userId, Long noteId, NoteUpdateDTO dto) {
        EmotionNote note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        checkOwnership(note, userId);

        boolean needUpdate = false;
        if (dto.getMood() != null) {
            note.setMood(dto.getMood());
            needUpdate = true;
        }
        if (dto.getContent() != null && !dto.getContent().isBlank()) {
            note.setContent(encryptor.encrypt(dto.getContent()));
            needUpdate = true;
        }
        if (needUpdate) {
            noteMapper.updateById(note);
            log.info("编辑心情便签: noteId={}, userId={}", noteId, userId);
        }
    }

    @Override
    public void deleteNote(Long userId, Long noteId) {
        EmotionNote note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        checkOwnership(note, userId);
        noteMapper.deleteById(noteId);
        log.info("删除心情便签: noteId={}, userId={}", noteId, userId);
    }

    // ── 内部方法 ──

    /**
     * 校验便签归属权。非本人 → 1003。
     */
    private void checkOwnership(EmotionNote note, Long userId) {
        if (!note.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    /**
     * 解密 content，失败时返回 {@value #DECRYPT_FAILED} 而非抛异常。
     */
    private String decryptSafely(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return "";
        }
        try {
            return encryptor.decrypt(ciphertext);
        } catch (Exception e) {
            log.warn("便签内容解密失败: {}", e.getMessage());
            return DECRYPT_FAILED;
        }
    }

    /**
     * 生成列表项 VO：解密 content 后取前 {@value #EXCERPT_LENGTH} 字摘要。
     */
    private NoteListItemVO toListVO(EmotionNote note) {
        String plainContent = decryptSafely(note.getContent());
        String excerpt = plainContent.length() > EXCERPT_LENGTH
                ? plainContent.substring(0, EXCERPT_LENGTH)
                : plainContent;
        return new NoteListItemVO(
                note.getId(),
                note.getMood(),
                note.getNoteDate(),
                note.getCreateTime(),
                excerpt
        );
    }

    /**
     * 解析 noteDate 字符串（yyyy-MM-dd），null 或空 → 今天；格式错误 → null。
     */
    private LocalDate parseNoteDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("noteDate 格式错误，忽略: {}", dateStr);
            return null;
        }
    }
}
