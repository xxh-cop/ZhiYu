-- 会话总结为 AES-GCM 密文 + Base64，100 字中文约 440 字符，
-- 原 VARCHAR(255) 会在真实对话中触发 Data too long。
-- 改为 TEXT，可重复执行。
ALTER TABLE emotion_session
  MODIFY COLUMN summary TEXT DEFAULT NULL COMMENT '会话总结(加密)';
