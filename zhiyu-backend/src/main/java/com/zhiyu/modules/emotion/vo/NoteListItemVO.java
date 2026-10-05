package com.zhiyu.modules.emotion.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 便签列表项 VO（M3-A1）。
 *
 * <p>对应 {@code GET /api/emotion/notes} 列表中的单条记录。
 * {@code excerpt} 为解密后内容的前 30 字摘要。
 */
@Data
public class NoteListItemVO {

    private Long id;
    private String mood;
    private LocalDate noteDate;
    private LocalDateTime createTime;

    /** 解密后内容的前 30 字摘要 */
    private String excerpt;

    public NoteListItemVO(Long id, String mood, LocalDate noteDate, LocalDateTime createTime, String excerpt) {
        this.id = id;
        this.mood = mood;
        this.noteDate = noteDate;
        this.createTime = createTime;
        this.excerpt = excerpt;
    }
}
