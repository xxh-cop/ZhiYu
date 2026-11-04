-- I1 审核理由落库 + I2 AI 审计对象级关联（2026-11-04）。
-- 幂等：information_schema 检查后加列，重复执行安全。
USE zhiyu;
SET NAMES utf8mb4;

-- I1：作品审核理由（驳回必填；历史数据为 NULL，读取兼容）
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='work' AND COLUMN_NAME='audit_reason')=0,
 'ALTER TABLE `work` ADD COLUMN `audit_reason` VARCHAR(255) DEFAULT NULL COMMENT ''审核理由；驳回必填，通过可空''','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- I1：评论审核理由
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='work_comment' AND COLUMN_NAME='audit_reason')=0,
 'ALTER TABLE `work_comment` ADD COLUMN `audit_reason` VARCHAR(255) DEFAULT NULL COMMENT ''审核理由；驳回必填，通过可空''','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- I2：AI 审计日志对象级关联（历史数据为 NULL；聚合查询对不上返回空数组，不伪造）
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_audit_log' AND COLUMN_NAME='ref_type')=0,
 'ALTER TABLE `ai_audit_log` ADD COLUMN `ref_type` VARCHAR(30) DEFAULT NULL COMMENT ''关联对象类型：work/comment 等（2026-11-04 起新写入携带）''','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_audit_log' AND COLUMN_NAME='ref_id')=0,
 'ALTER TABLE `ai_audit_log` ADD COLUMN `ref_id` BIGINT DEFAULT NULL COMMENT ''关联对象 id（雪花）''','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_audit_log' AND INDEX_NAME='idx_audit_ref')=0,
 'ALTER TABLE `ai_audit_log` ADD INDEX `idx_audit_ref` (`ref_type`,`ref_id`)','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
