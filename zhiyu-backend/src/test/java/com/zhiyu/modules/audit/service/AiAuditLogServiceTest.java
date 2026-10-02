package com.zhiyu.modules.audit.service;

import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.modules.audit.entity.AiAuditLog;
import com.zhiyu.modules.audit.mapper.AiAuditLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AiAuditLogService} 集成单测（T1.3b）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AiAuditLogServiceTest {

    @Autowired
    private AiAuditLogService aiAuditLogService;

    @Autowired
    private AiAuditLogMapper aiAuditLogMapper;

    @Autowired
    private AesEncryptor encryptor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("log 写入数据库")
    void testLog_Write() {
        aiAuditLogService.log(7001L, "chat", "用户输入", "AI 输出", "PASS", "通过");

        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM ai_audit_log WHERE scene = 'chat' AND reason = '通过'",
                Long.class);
        assertNotNull(id);
        AiAuditLog log = aiAuditLogMapper.selectById(id);
        assertEquals("chat", log.getScene());
        assertEquals("PASS", log.getAction());
    }

    @Test
    @DisplayName("raw_input / ai_output 是密文（用 JdbcTemplate 读原始列验证）")
    void testLog_EncryptedColumns() {
        String rawInput = "我想测试加密";
        String aiOutput = "这是 AI 回复";
        aiAuditLogService.log(7002L, "chat", rawInput, aiOutput, "PASS", "加密验证");

        String storedInput = jdbcTemplate.queryForObject(
                "SELECT raw_input FROM ai_audit_log WHERE reason = '加密验证'",
                String.class);
        String storedOutput = jdbcTemplate.queryForObject(
                "SELECT ai_output FROM ai_audit_log WHERE reason = '加密验证'",
                String.class);

        assertNotNull(storedInput);
        assertNotNull(storedOutput);
        // 不是明文
        assertNotEquals(rawInput, storedInput, "raw_input 应为密文");
        assertNotEquals(aiOutput, storedOutput, "ai_output 应为密文");
        // 解密后等于明文
        assertEquals(rawInput, encryptor.decrypt(storedInput));
        assertEquals(aiOutput, encryptor.decrypt(storedOutput));
    }

    @Test
    @DisplayName("null 值安全")
    void testLog_NullSafe() {
        aiAuditLogService.log(7003L, "chat", null, null, "PASS", "null 验证");

        String storedInput = jdbcTemplate.queryForObject(
                "SELECT raw_input FROM ai_audit_log WHERE reason = 'null 验证'",
                String.class);
        String storedOutput = jdbcTemplate.queryForObject(
                "SELECT ai_output FROM ai_audit_log WHERE reason = 'null 验证'",
                String.class);
        assertNull(storedInput);
        assertNull(storedOutput);
    }
}
