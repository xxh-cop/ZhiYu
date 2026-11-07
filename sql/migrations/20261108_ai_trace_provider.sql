-- 按能力分档路由·ai_trace provider 列（2026-11-08 装配层批）：
-- 云端/本地兜底双端点后，仅凭 model 名无法可靠区分实际 provider（云与本地可同名）。
-- 口径：provider 仅作运行元数据归属（deepseek=云端、ollama=本地、mock=本地降级实现）；
-- 存量行保持 NULL（加列前的调用不冒充任何 provider）；ai_audit_log 不加此列（D6 分工：
-- 审计管内容安全，trace 管用量口径）。
-- 幂等：information_schema 检查后执行，重复执行安全。
USE zhiyu;
SET NAMES utf8mb4;

SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_trace' AND COLUMN_NAME='provider')=0,
 'ALTER TABLE `ai_trace` ADD COLUMN `provider` VARCHAR(20) DEFAULT NULL COMMENT ''实际服务 provider（deepseek/ollama/mock；null=加列前存量或未发起调用）'' AFTER `model`','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
