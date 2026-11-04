-- AY1（2026-11-05）：craft_project.audit_reason——下架/删除理由落库（参照 work.audit_reason）。
-- 顺带把 auditProject 的驳回理由也落同列（此前校验非空后丢弃）。
-- 幂等：information_schema 检查后加列，重复执行安全。
USE zhiyu;
SET NAMES utf8mb4;

SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='craft_project' AND COLUMN_NAME='audit_reason')=0,
 'ALTER TABLE `craft_project` ADD COLUMN `audit_reason` VARCHAR(300) DEFAULT NULL COMMENT ''审核/下架/删除理由（AY1 2026-11-05；驳回理由同列落库）'' AFTER `status`',
 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
