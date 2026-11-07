-- 软档影子模式·模型归属列（2026-11-07）：演示可走本地模型前，先能区分判定归属。
-- 口径：存量行由 ADD COLUMN 的 DEFAULT 'unknown' 自动补齐——显式未知标记，绝不默认当成云端；
-- 幂等：information_schema 检查后执行，重复执行安全（第二遍 SELECT 1 空转，不改写任何已有值）。
-- 注意：本脚本是新增增量，已执行过的 20261107_moderation_soft_shadow.sql 不回改。
USE zhiyu;
SET NAMES utf8mb4;

SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='moderation_soft_shadow' AND COLUMN_NAME='provider')=0,
 'ALTER TABLE `moderation_soft_shadow` ADD COLUMN `provider` VARCHAR(30) NOT NULL DEFAULT ''unknown'' COMMENT ''判定归属 provider（deepseek=云端生产/本地演示另值；unknown=2026-11-07 加列前存量或尚无成功判定）'' AFTER `audit_log_id`','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='moderation_soft_shadow' AND COLUMN_NAME='model')=0,
 'ALTER TABLE `moderation_soft_shadow` ADD COLUMN `model` VARCHAR(80) NOT NULL DEFAULT ''unknown'' COMMENT ''判定归属模型名（与 provider 同源；unknown 同上）'' AFTER `provider`','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
