-- Upgrade existing installations; CREATE TABLE IF NOT EXISTS cannot add columns.
-- Additive, repeatable migration. Existing work rows keep the historical image default.
USE zhiyu;
SET NAMES utf8mb4;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='work' AND COLUMN_NAME='media_type')=0,
 'ALTER TABLE `work` ADD COLUMN `media_type` VARCHAR(10) NOT NULL DEFAULT ''image'' COMMENT ''image/video/text''','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='work' AND COLUMN_NAME='video_url')=0,
 'ALTER TABLE `work` ADD COLUMN `video_url` VARCHAR(255) DEFAULT NULL','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='craft_session' AND COLUMN_NAME='emotion_session_id')=0,
 'ALTER TABLE `craft_session` ADD COLUMN `emotion_session_id` BIGINT DEFAULT NULL','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
