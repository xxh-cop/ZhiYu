package com.zhiyu.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.dto.PageVO;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.admin.service.AdminAuditLogService;
import com.zhiyu.modules.admin.vo.AuditLogDetailVO;
import com.zhiyu.modules.admin.vo.AuditLogItemVO;
import com.zhiyu.modules.audit.entity.AiAuditLog;
import com.zhiyu.modules.audit.mapper.AiAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link AdminAuditLogService} 实现（P1-A1）。
 *
 * <p>复用 {@link AiAuditLogMapper}（BaseMapper）与 {@link AesEncryptor}。
 * 列表查询不返回 rawInput / aiOutput；详情查询解密后返回。
 * 解密失败时返回空字符串并记录日志，不抛 500（AGENTS.md 可能的坑 §3）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditLogServiceImpl implements AdminAuditLogService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AiAuditLogMapper auditLogMapper;
    private final AesEncryptor encryptor;

    @Override
    public PageVO<AuditLogItemVO> listAuditLogs(Integer page, Integer pageSize, String scene, String action,
                                                 String startTime, String endTime) {
        int pageNum = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);

        LambdaQueryWrapper<AiAuditLog> wrapper = new LambdaQueryWrapper<>();
        if (scene != null && !scene.isBlank()) {
            wrapper.eq(AiAuditLog::getScene, scene);
        }
        if (action != null && !action.isBlank()) {
            wrapper.eq(AiAuditLog::getAction, action);
        }
        if (startTime != null && !startTime.isBlank()) {
            try {
                LocalDateTime start = LocalDateTime.parse(startTime, DATE_TIME_FORMATTER);
                wrapper.ge(AiAuditLog::getCreateTime, start);
            } catch (Exception e) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "startTime 格式应为 yyyy-MM-dd HH:mm:ss");
            }
        }
        if (endTime != null && !endTime.isBlank()) {
            try {
                LocalDateTime end = LocalDateTime.parse(endTime, DATE_TIME_FORMATTER);
                wrapper.le(AiAuditLog::getCreateTime, end);
            } catch (Exception e) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "endTime 格式应为 yyyy-MM-dd HH:mm:ss");
            }
        }
        wrapper.orderByDesc(AiAuditLog::getCreateTime);

        IPage<AiAuditLog> result = auditLogMapper.selectPage(new Page<>(pageNum, size), wrapper);

        List<AuditLogItemVO> records = result.getRecords().stream()
                .map(this::toItemVO)
                .collect(Collectors.toList());

        return new PageVO<>(result.getTotal(), pageNum, size, records);
    }

    @Override
    public AuditLogDetailVO getAuditLog(Long id) {
        AiAuditLog log = auditLogMapper.selectById(id);
        if (log == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        AuditLogDetailVO vo = new AuditLogDetailVO();
        vo.setId(log.getId());
        vo.setScene(log.getScene());
        vo.setAction(log.getAction());
        vo.setReason(log.getReason());
        vo.setCreateTime(log.getCreateTime());

        // 解密 rawInput / aiOutput，失败时返回空字符串
        vo.setRawInput("[隐私保护：不提供原始输入]");
        vo.setAiOutput("[隐私保护：不提供原始回复]");

        return vo;
    }

    /**
     * 安全解密：失败时返回 [解密失败] 占位文本并记录日志，不抛异常。
     */
    private String decryptSafely(String ciphertext, Long logId, String fieldName) {
        if (ciphertext == null) {
            return null;
        }
        try {
            return encryptor.decrypt(ciphertext);
        } catch (Exception e) {
            log.warn("审计日志字段解密失败: logId={}, field={}, error={}", logId, fieldName, e.getMessage());
            return "[解密失败]";
        }
    }

    private AuditLogItemVO toItemVO(AiAuditLog log) {
        AuditLogItemVO vo = new AuditLogItemVO();
        vo.setId(log.getId());
        vo.setScene(log.getScene());
        vo.setAction(log.getAction());
        vo.setReason(log.getReason());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }
}
