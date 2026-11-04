-- AU1 CARE_RISK 字典对齐（2026-11-04）：risk_level 语义由「1一般 2高危」升级为
-- 「1关注 2高危 3紧急」（AQ2 起后端已按词表分组真实分级，本迁移只更新列注释这一
-- 字典载体，不修改任何历史数据——旧记录 risk_level 值不变，仅语义标签更新）。
-- 说明：CARE_RISK 不是数据库字典表（dict_mood/dict_category 才是），其语义载体是
-- 本列注释 + 管理端 status-dict.ts 常量；故本迁移以 ALTER MODIFY COLUMN 更新注释。
-- 幂等：information_schema 检查列注释，已是新口径则跳过，重复执行安全。
USE zhiyu;
SET NAMES utf8mb4;

SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='care_reminder'
               AND COLUMN_NAME='risk_level' AND COLUMN_COMMENT='1关注 2高危 3紧急')=0,
 'ALTER TABLE `care_reminder` MODIFY COLUMN `risk_level` TINYINT NOT NULL COMMENT ''1关注 2高危 3紧急''',
 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- crisis_session_log 为 AJ 批新增表，旧库可能尚无此表：表不存在或注释已新则跳过
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='crisis_session_log'
               AND COLUMN_NAME='risk_level'
               AND COLUMN_COMMENT<>'危机等级（1关注 2高危 3紧急，与 care_reminder 口径一致）')>0,
 'ALTER TABLE `crisis_session_log` MODIFY COLUMN `risk_level` TINYINT NOT NULL DEFAULT 2 COMMENT ''危机等级（1关注 2高危 3紧急，与 care_reminder 口径一致）''',
 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
