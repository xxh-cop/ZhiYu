-- ============================================================
-- 20261026_ai_audit_user_id.sql
-- 目的：ai_audit_log 增加 user_id，使 AI 审计可按用户追溯。
--
-- 备份（执行前）：
--   mysqldump -u<user> -p <db> ai_audit_log > backup_ai_audit_log_20261026.sql
--
-- 重复执行策略：
--   MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，因此先查 information_schema：
--   列 / 索引已存在时跳过，可安全重复执行（首次建列+索引，之后均为 no-op）。
--
-- 历史数据：
--   迁移前的行 user_id 为 NULL（无法回溯归属），不做猜测填充；
--   迁移后由应用层（AiAuditLogServiceImpl）保证每次写入都带 userId。
--   审计日志只追加、不更新，本脚本不回写历史行。
--
-- 回滚（确认无新数据依赖后）：
--   ALTER TABLE ai_audit_log DROP INDEX idx_audit_user;
--   ALTER TABLE ai_audit_log DROP COLUMN user_id;
-- ============================================================

SET @has_user_id := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_audit_log' AND COLUMN_NAME = 'user_id'
);
SET @ddl := IF(@has_user_id = 0,
  'ALTER TABLE ai_audit_log ADD COLUMN user_id BIGINT DEFAULT NULL COMMENT ''关联用户ID；迁移前历史行为 NULL，新写入必须带'' AFTER id',
  'SELECT ''ai_audit_log.user_id already exists, skipped'' AS migration_status');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_user_idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_audit_log' AND INDEX_NAME = 'idx_audit_user'
);
SET @ddl := IF(@has_user_idx = 0,
  'ALTER TABLE ai_audit_log ADD KEY idx_audit_user (user_id)',
  'SELECT ''idx_audit_user already exists, skipped'' AS migration_status');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
