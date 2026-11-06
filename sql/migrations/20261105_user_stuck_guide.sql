-- 卡点引导（stuck guidance）：用户级总开关（2026-11-05，设计稿 docs/stuck-guidance-design-20261105.md）。
-- 幂等：information_schema 检查后加列，重复执行安全。
USE zhiyu;
SET NAMES utf8mb4;

-- 引导开关（默认开：与 baseline_nudge_enabled 同口径；用户可在设置中永久关闭）
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='stuck_guide_enabled')=0,
 'ALTER TABLE `user` ADD COLUMN `stuck_guide_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT ''卡点引导开关（1 开 0 关；同一步骤反复停留时给出可选动作建议的轻提示）''','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
