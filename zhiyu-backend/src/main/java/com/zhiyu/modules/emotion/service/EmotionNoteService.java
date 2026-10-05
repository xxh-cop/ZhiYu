package com.zhiyu.modules.emotion.service;

import com.zhiyu.common.dto.PageVO;
import com.zhiyu.modules.emotion.dto.NoteCreateDTO;
import com.zhiyu.modules.emotion.dto.NoteUpdateDTO;
import com.zhiyu.modules.emotion.vo.NoteDetailVO;
import com.zhiyu.modules.emotion.vo.NoteListItemVO;

/**
 * 心情便签 Service（M3-A1）。
 *
 * <p>所有方法内部强制 {@code userId = UserContext.getUserId()}，仅本人可见。
 * content 字段在 Service 层加解密（{@link com.zhiyu.common.crypto.AesEncryptor}）。
 */
public interface EmotionNoteService {

    /**
     * 创建便签。
     *
     * @param userId 当前登录用户
     * @param dto    创建 DTO
     * @return 新便签 id
     */
    Long createNote(Long userId, NoteCreateDTO dto);

    /**
     * 分页查询便签列表。
     *
     * @param userId    当前登录用户
     * @param page      页码（从 1 开始）
     * @param pageSize  每页大小
     * @param startDate 起始日期（可选）
     * @param endDate   截止日期（可选）
     * @return 分页列表
     */
    PageVO<NoteListItemVO> listNotes(Long userId, int page, int pageSize,
                                      String startDate, String endDate);

    /**
     * 查询便签详情。
     *
     * @param userId 当前登录用户
     * @param noteId 便签 id
     * @return 详情 VO
     * @throws com.zhiyu.common.exception.BusinessException 1004（不存在） / 1003（非本人）
     */
    NoteDetailVO getNoteDetail(Long userId, Long noteId);

    /**
     * 编辑便签（仅更新非 null 字段）。
     *
     * @param userId 当前登录用户
     * @param noteId 便签 id
     * @param dto    编辑 DTO
     * @throws com.zhiyu.common.exception.BusinessException 1004 / 1003
     */
    void updateNote(Long userId, Long noteId, NoteUpdateDTO dto);

    /**
     * 逻辑删除便签。
     *
     * @param userId 当前登录用户
     * @param noteId 便签 id
     * @throws com.zhiyu.common.exception.BusinessException 1004 / 1003
     */
    void deleteNote(Long userId, Long noteId);
}
