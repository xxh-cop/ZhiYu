package com.zhiyu.modules.audit.service.impl;

import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.modules.audit.entity.AiAuditLog;
import com.zhiyu.modules.audit.mapper.AiAuditLogMapper;
import com.zhiyu.modules.audit.service.AiAuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * {@link AiAuditLogService} 实现（T1.3b）。
 *
 * <p>{@code rawInput} / {@code aiOutput} 用 {@link AesEncryptor} 加密后落库；
 * {@code null} 保持 {@code null}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAuditLogServiceImpl implements AiAuditLogService {

    private final AiAuditLogMapper auditLogMapper;
    private final AesEncryptor encryptor;

    @Override
    public void log(Long userId, String scene, String rawInput, String aiOutput,
                    String action, String reason) {
        log(userId, scene, rawInput, aiOutput, action, reason, null, null);
    }

    @Override
    public void log(Long userId, String scene, String rawInput, String aiOutput,
                    String action, String reason, String refType, Long refId) {
        AiAuditLog logEntry = new AiAuditLog();
        logEntry.setUserId(userId);
        logEntry.setScene(scene);
        logEntry.setRawInput(encryptor.encrypt(rawInput));
        logEntry.setAiOutput(encryptor.encrypt(aiOutput));
        logEntry.setAction(action);
        logEntry.setReason(reason);
        logEntry.setRefType(refType);
        logEntry.setRefId(refId);
        auditLogMapper.insert(logEntry);
        log.debug("审计日志写入: userId={}, scene={}, action={}, reason={}, logId={}",
                userId, scene, action, reason, logEntry.getId());
    }
}
