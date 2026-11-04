-- AM1 情绪基线偏离提醒：用户级开关与冷却（2026-11-04）。
-- 幂等：information_schema 检查后加列，重复执行安全。
USE zhiyu;
SET NAMES utf8mb4;

-- 提醒开关（默认开：本批目标是做成真实能力；用户可在设置中关闭）
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='baseline_nudge_enabled')=0,
 'ALTER TABLE `user` ADD COLUMN `baseline_nudge_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT ''情绪基线偏离提醒开关（1 开 0 关，AM1）''','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 冷却：上次提示时间与档位
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='baseline_nudge_last_at')=0,
 'ALTER TABLE `user` ADD COLUMN `baseline_nudge_last_at` DATETIME DEFAULT NULL COMMENT ''基线提醒上次提示时间（冷却用）''','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='baseline_nudge_last_level')=0,
 'ALTER TABLE `user` ADD COLUMN `baseline_nudge_last_level` VARCHAR(16) DEFAULT NULL COMMENT ''基线提醒上次提示档位''','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
