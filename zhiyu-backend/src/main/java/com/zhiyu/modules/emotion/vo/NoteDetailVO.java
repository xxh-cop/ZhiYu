package com.zhiyu.modules.emotion.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 便签详情 VO（M3-A1）。
 *
 * <p>对应 {@code GET /api/emotion/notes/{id}}。
 * {@code content} 为解密后的明文。
 */
@Data
public class NoteDetailVO {

    private Long id;
    private String mood;

    /** 解密后的明文内容 */
    private String content;

    private LocalDate noteDate;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public NoteDetailVO(Long id, String mood, String content, LocalDate noteDate,
                        LocalDateTime createTime, LocalDateTime updateTime) {
        this.id = id;
        this.mood = mood;
        this.content = content;
        this.noteDate = noteDate;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }
}
